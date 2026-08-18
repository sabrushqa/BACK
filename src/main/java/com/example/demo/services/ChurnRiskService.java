package com.example.demo.services;

import com.example.demo.dto.SupervisorRiskOverviewResponse;
import com.example.demo.entities.Reclamation;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.ReclamationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Page superviseur "Risque d'abandon" : agrege l'historique reel du switch
 * monetique en variables (voir lana-merchant-intelligence/features.py),
 * interroge le modele de scoring pour chaque commercant qui a un historique
 * exploitable, puis produit deux lectures metier :
 * - le classement des commercants/secteurs les plus a risque,
 * - le taux de refus par canal (TPE vs e-commerce) par secteur, pour reperer
 *   ou l'encaissement physique ou le paiement digital pose probleme.
 *
 * Degradation gracieuse : un commercant sans transaction n'est pas scorable
 * (features non calculables) et est simplement exclu, comptabilise dans
 * commercantsIgnores plutot que de faire echouer toute la page.
 */
@Service
@Transactional(readOnly = true)
public class ChurnRiskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChurnRiskService.class);
    private static final Set<String> APPROVED_STATUSES = Set.of("APPROVED", "ACCEPTE", "APPROUVE", "VALIDE");
    private static final String UNKNOWN = "NON_RENSEIGNE";

    private final CommercantRepository commercantRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final ReclamationRepository reclamationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SwitchMonetiqueClient switchMonetiqueClient;
    private final ChurnModelClient churnModelClient;
    private final JwtService jwtService;

    public ChurnRiskService(
        CommercantRepository commercantRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        ReclamationRepository reclamationRepository,
        UtilisateurRepository utilisateurRepository,
        SwitchMonetiqueClient switchMonetiqueClient,
        ChurnModelClient churnModelClient,
        JwtService jwtService
    ) {
        this.commercantRepository = commercantRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.reclamationRepository = reclamationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.switchMonetiqueClient = switchMonetiqueClient;
        this.churnModelClient = churnModelClient;
        this.jwtService = jwtService;
    }

    public SupervisorRiskOverviewResponse getRiskOverview(String authorizationHeader) {
        readAuthenticatedSupervisor(authorizationHeader);

        // Partage entre tous les appels Oracle de ce calcul (pas un champ
        // d'instance : ChurnRiskService est un singleton partage entre
        // requetes concurrentes, un booleen partage corromprait le resultat
        // d'un superviseur avec l'echec d'un autre).
        java.util.concurrent.atomic.AtomicBoolean switchIndisponible =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        List<SwitchMonetiqueClient.SwitchTpe> oracleStock = fetchOracleStockSafely(switchIndisponible);
        List<SupervisorRiskOverviewResponse.MerchantRiskItem> merchantItems = new ArrayList<>();
        // Cumule, par (secteur, canal), le nombre de transactions et de refus —
        // toutes canal/secteur confondus, independamment du scoring IA.
        Map<String, long[]> canalCounters = new java.util.LinkedHashMap<>(); // key "SECTEUR|CANAL" -> [total, refus]
        // Intensite d'usage du TPE par secteur (nb de TPE actifs vs nb de
        // transactions TPE reellement passees) — vue business independante du
        // modele, pour cibler les secteurs sous-equipes/sous-utilisateurs.
        Map<String, int[]> tpeUsageBySector = new java.util.LinkedHashMap<>(); // secteur -> [tpeCount, tpeTransactions]
        int ignored = 0;

        for (commercant merchant : commercantRepository.findAll()) {
            List<SwitchMonetiqueClient.SwitchTransaction> transactions =
                fetchOracleTransactionsSafely(merchant.getIdCommercant(), switchIndisponible);
            if (transactions.isEmpty()) {
                ignored++;
                continue;
            }

            String secteur = firstNotBlankOrDefault(merchant.getSecteur(), UNKNOWN);
            for (SwitchMonetiqueClient.SwitchTransaction transaction : transactions) {
                String canal = firstNotBlankOrDefault(transaction.canal(), UNKNOWN);
                long[] counters = canalCounters.computeIfAbsent(secteur + "|" + canal, key -> new long[2]);
                counters[0]++;
                if (!APPROVED_STATUSES.contains(safe(transaction.statut()).toUpperCase(java.util.Locale.ROOT))) {
                    counters[1]++;
                }
            }

            LocalDate reference = transactions.stream()
                .map(SwitchMonetiqueClient.SwitchTransaction::dateTransaction)
                .filter(java.util.Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
            if (reference == null) {
                ignored++;
                continue;
            }

            ChurnFeatures features = computeFeatures(transactions, reference, merchant, oracleStock);

            if (features.nombreTpe() > 0) {
                long tpeTransactionCount = transactions.stream()
                    .filter(t -> "TPE".equalsIgnoreCase(safe(t.canal())))
                    .count();
                int[] usage = tpeUsageBySector.computeIfAbsent(secteur, key -> new int[2]);
                usage[0] += features.nombreTpe();
                usage[1] += (int) tpeTransactionCount;
            }

            ChurnModelClient.RiskPredictionResponse prediction;
            try {
                prediction = churnModelClient.predict(features.toRequest(merchant.getIdCommercant()));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                    "[ChurnRiskService] lana-merchant-intelligence injoignable, "
                        + "commerçant {} exclu du classement de risque.",
                    merchant.getIdCommercant(),
                    exception
                );
                ignored++;
                continue;
            }

            merchantItems.add(new SupervisorRiskOverviewResponse.MerchantRiskItem(
                merchant.getIdCommercant(),
                firstNotBlankOrDefault(firstNotBlank(merchant.getNomCommercial(), merchant.getRaisonSociale()), "Commerçant #" + merchant.getIdCommercant()),
                secteur,
                firstNotBlankOrDefault(merchant.getRegion(), UNKNOWN),
                resolveTypeAffiliation(merchant.getIdCommercant()),
                prediction.scoreRisque(),
                safe(prediction.niveauRisque()),
                prediction.raisons() == null ? List.of() : prediction.raisons(),
                safe(prediction.actionRecommandee())
            ));
        }

        merchantItems.sort(Comparator.comparingDouble(SupervisorRiskOverviewResponse.MerchantRiskItem::scoreRisque).reversed());

        List<SupervisorRiskOverviewResponse.SectorRiskItem> sectorRisk = merchantItems.stream()
            .collect(Collectors.groupingBy(SupervisorRiskOverviewResponse.MerchantRiskItem::secteur))
            .entrySet()
            .stream()
            .map(entry -> {
                List<SupervisorRiskOverviewResponse.MerchantRiskItem> items = entry.getValue();
                double avg = items.stream().mapToDouble(SupervisorRiskOverviewResponse.MerchantRiskItem::scoreRisque).average().orElse(0);
                long eleve = items.stream().filter(i -> "ELEVE".equals(i.niveauRisque())).count();
                return new SupervisorRiskOverviewResponse.SectorRiskItem(entry.getKey(), items.size(), round1(avg), (int) eleve);
            })
            .sorted(Comparator.comparingDouble(SupervisorRiskOverviewResponse.SectorRiskItem::scoreMoyen).reversed())
            .toList();

        List<SupervisorRiskOverviewResponse.SectorCanalItem> canalPerformance = canalCounters.entrySet().stream()
            .map(entry -> {
                String[] parts = entry.getKey().split("\\|", 2);
                long total = entry.getValue()[0];
                long refus = entry.getValue()[1];
                double taux = total == 0 ? 0 : round1((refus * 100.0) / total);
                return new SupervisorRiskOverviewResponse.SectorCanalItem(parts[0], parts[1], (int) total, taux);
            })
            // Un secteur avec 1 ou 2 transactions ne dit rien de fiable sur la
            // qualite d'un canal de paiement — on l'exclut pour ne pas induire
            // le superviseur en erreur sur un echantillon trop petit.
            // Tri par secteur (puis canal) plutôt que par pire taux de refus :
            // l'objectif est la comparaison TPE vs e-commerce au sein d'un même
            // secteur (qui fonctionne bien avec quel canal, pas seulement les
            // pires cas).
            .filter(item -> item.nombreTransactions() >= 5)
            .sorted(
                Comparator.comparing(SupervisorRiskOverviewResponse.SectorCanalItem::secteur)
                    .thenComparing(SupervisorRiskOverviewResponse.SectorCanalItem::canal)
            )
            .toList();

        List<SupervisorRiskOverviewResponse.SectorTpeUsageItem> usageTpeParSecteur = tpeUsageBySector.entrySet()
            .stream()
            .map(entry -> {
                int tpeCount = entry.getValue()[0];
                int tpeTransactions = entry.getValue()[1];
                double parTpe = tpeCount == 0 ? 0 : round1(tpeTransactions / (double) tpeCount);
                return new SupervisorRiskOverviewResponse.SectorTpeUsageItem(entry.getKey(), tpeCount, tpeTransactions, parTpe);
            })
            // Usage croissant en premier : les secteurs qui sous-utilisent le
            // TPE (candidats a une offre/accompagnement) remontent avant les
            // gros utilisateurs (candidats a une offre de fidelisation).
            .sorted(Comparator.comparingDouble(SupervisorRiskOverviewResponse.SectorTpeUsageItem::transactionsParTpe))
            .toList();

        double scoreMoyenGlobal = merchantItems.stream()
            .mapToDouble(SupervisorRiskOverviewResponse.MerchantRiskItem::scoreRisque)
            .average()
            .orElse(0);
        int eleve = (int) merchantItems.stream().filter(i -> "ELEVE".equals(i.niveauRisque())).count();
        int moyen = (int) merchantItems.stream().filter(i -> "MOYEN".equals(i.niveauRisque())).count();
        int faible = (int) merchantItems.stream().filter(i -> "FAIBLE".equals(i.niveauRisque())).count();

        return new SupervisorRiskOverviewResponse(
            merchantItems.size(),
            ignored,
            round1(scoreMoyenGlobal),
            eleve,
            moyen,
            faible,
            merchantItems,
            sectorRisk,
            canalPerformance,
            usageTpeParSecteur,
            switchIndisponible.get()
        );
    }

    private record ChurnFeatures(
        double ca7j, double ca30j, double ca90j,
        int tx7j, int tx30j, int tx90j,
        double panierMoyen30j, double tauxRefus30j,
        int joursSansTransaction, double variationCa30j,
        int nombreTpe, int nombreReclamations90j,
        String secteur, String region
    ) {
        ChurnModelClient.MerchantFeaturesRequest toRequest(Long commercantId) {
            return new ChurnModelClient.MerchantFeaturesRequest(
                commercantId, ca7j, ca30j, ca90j, tx7j, tx30j, tx90j,
                panierMoyen30j, tauxRefus30j, joursSansTransaction, variationCa30j,
                nombreTpe, nombreReclamations90j, secteur, region
            );
        }
    }

    private ChurnFeatures computeFeatures(
        List<SwitchMonetiqueClient.SwitchTransaction> transactions,
        LocalDate reference,
        commercant merchant,
        List<SwitchMonetiqueClient.SwitchTpe> oracleStock
    ) {
        List<SwitchMonetiqueClient.SwitchTransaction> approved = transactions.stream()
            .filter(t -> APPROVED_STATUSES.contains(safe(t.statut()).toUpperCase(java.util.Locale.ROOT)))
            .toList();

        double ca7 = sumInWindow(approved, reference, 7);
        double ca30 = sumInWindow(approved, reference, 30);
        double ca90 = sumInWindow(approved, reference, 90);
        int tx7 = countInWindow(approved, reference, 7);
        int tx30 = countInWindow(approved, reference, 30);
        int tx90 = countInWindow(approved, reference, 90);

        LocalDate last30Start = reference.minusDays(29);
        LocalDate previous30Start = reference.minusDays(59);
        LocalDate previous30End = last30Start.minusDays(1);
        double ca30Previous = sumBetween(approved, previous30Start, previous30End);
        double variationCa30j = ca30Previous == 0 ? 0.0 : clip((ca30 - ca30Previous) / ca30Previous, -1.0, 5.0);

        int total30 = countBetween(transactions, last30Start, reference);
        int accepted30 = countBetween(approved, last30Start, reference);
        double tauxRefus30j = total30 == 0 ? 0.0 : (total30 - accepted30) / (double) total30;

        LocalDate lastAccepted = approved.stream()
            .map(SwitchMonetiqueClient.SwitchTransaction::dateTransaction)
            .filter(java.util.Objects::nonNull)
            .map(LocalDateTime::toLocalDate)
            .max(Comparator.naturalOrder())
            .orElse(null);
        int joursSansTransaction = lastAccepted == null
            ? 999
            : (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(lastAccepted, reference));

        double panierMoyen30j = tx30 == 0 ? 0.0 : ca30 / tx30;

        String idCommercant = merchant.getIdCommercant().toString();
        int nombreTpe = (int) oracleStock.stream().filter(tpe -> idCommercant.equals(tpe.idCommercant())).count();

        LocalDate reclamationCutoff = reference.minusDays(89);
        int nombreReclamations90j = (int) reclamationRepository
            .findByCommercant_IdCommercantOrderByDateCreationDesc(merchant.getIdCommercant())
            .stream()
            .map(Reclamation::getDateCreation)
            .filter(date -> date != null && !date.isBefore(reclamationCutoff) && !date.isAfter(reference))
            .count();

        return new ChurnFeatures(
            ca7, ca30, ca90, tx7, tx30, tx90,
            panierMoyen30j, tauxRefus30j, joursSansTransaction, variationCa30j,
            nombreTpe, nombreReclamations90j,
            firstNotBlankOrDefault(merchant.getSecteur(), UNKNOWN),
            firstNotBlankOrDefault(merchant.getRegion(), UNKNOWN)
        );
    }

    private double sumInWindow(List<SwitchMonetiqueClient.SwitchTransaction> items, LocalDate reference, int days) {
        return sumBetween(items, reference.minusDays(days - 1L), reference);
    }

    private int countInWindow(List<SwitchMonetiqueClient.SwitchTransaction> items, LocalDate reference, int days) {
        return countBetween(items, reference.minusDays(days - 1L), reference);
    }

    private double sumBetween(List<SwitchMonetiqueClient.SwitchTransaction> items, LocalDate start, LocalDate end) {
        return items.stream()
            .filter(t -> withinRange(t, start, end))
            .map(SwitchMonetiqueClient.SwitchTransaction::montant)
            .filter(java.util.Objects::nonNull)
            .mapToDouble(BigDecimal::doubleValue)
            .sum();
    }

    private int countBetween(List<SwitchMonetiqueClient.SwitchTransaction> items, LocalDate start, LocalDate end) {
        return (int) items.stream().filter(t -> withinRange(t, start, end)).count();
    }

    private boolean withinRange(SwitchMonetiqueClient.SwitchTransaction transaction, LocalDate start, LocalDate end) {
        if (transaction.dateTransaction() == null || start.isAfter(end)) {
            return false;
        }
        LocalDate date = transaction.dateTransaction().toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String resolveTypeAffiliation(Long commercantId) {
        List<dossier_affiliation> dossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercantId);
        return dossiers.stream()
            .findFirst()
            .map(dossier_affiliation::getTypeAffiliation)
            .map(Enum::name)
            .orElse(UNKNOWN);
    }

    private List<SwitchMonetiqueClient.SwitchTransaction> fetchOracleTransactionsSafely(
        Long commercantId,
        java.util.concurrent.atomic.AtomicBoolean switchIndisponible
    ) {
        try {
            return switchMonetiqueClient.transactions(commercantId.toString());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "[ChurnRiskService] switch-monetique-service injoignable pour le commerçant {}.",
                commercantId,
                exception
            );
            switchIndisponible.set(true);
            return List.of();
        }
    }

    private List<SwitchMonetiqueClient.SwitchTpe> fetchOracleStockSafely(
        java.util.concurrent.atomic.AtomicBoolean switchIndisponible
    ) {
        try {
            return switchMonetiqueClient.stockComplet();
        } catch (RuntimeException exception) {
            LOGGER.warn("[ChurnRiskService] switch-monetique-service injoignable, stock TPE indisponible.", exception);
            switchIndisponible.set(true);
            return List.of();
        }
    }

    private void readAuthenticatedSupervisor(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification Keycloak requise."));

        if (jwtService.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak expirée.");
        }

        Long utilisateurId = jwtService.extractUserId(token);
        if (utilisateurId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token Keycloak invalide.");
        }

        utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session introuvable."));

        if (jwtService.isSessionInvalidated(token, utilisateur)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak invalidée.");
        }

        if (utilisateur.getRole() != RoleUser.SUPERVISEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès superviseur requis.");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNotBlankOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
