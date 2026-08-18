package com.example.demo.bootstrap;

import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.services.PasswordHashService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@ConditionalOnProperty(
    name = "app.demo.affiliation-seed.enabled",
    havingValue = "true"
)
public class AffiliationDemoSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AffiliationDemoSeeder.class);

    private final UtilisateurRepository utilisateurRepository;
    private final CommercantRepository commercantRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final CommercialeRepository commercialeRepository;
    private final BackOfficeRepository backOfficeRepository;
    private final PasswordHashService passwordHashService;
    private final int requestedCount;
    private final String emailPrefix;
    private final String defaultPassword;

    public AffiliationDemoSeeder(
        UtilisateurRepository utilisateurRepository,
        CommercantRepository commercantRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        CommercialeRepository commercialeRepository,
        BackOfficeRepository backOfficeRepository,
        PasswordHashService passwordHashService,
        @Value("${app.demo.affiliation-seed.count:50}") int requestedCount,
        @Value("${app.demo.affiliation-seed.e-mail-prefix:seed.affiliation.demo}") String emailPrefix,
        // Sonar S2068 : evite un mot de passe litteral en dur dans le code source —
        // configurable via env var (APP_DEMO_SEED_PASSWORD), avec la meme valeur par
        // defaut qu'avant pour ne rien changer en local/dev sans configuration.
        @Value("${app.demo.seed-password:SeedDemo123!}") String defaultPassword
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercantRepository = commercantRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.commercialeRepository = commercialeRepository;
        this.backOfficeRepository = backOfficeRepository;
        this.passwordHashService = passwordHashService;
        this.requestedCount = requestedCount;
        this.emailPrefix = emailPrefix;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (requestedCount <= 0) {
            LOGGER.info("Affiliation demo seed skipped because count={} is not positive.", requestedCount);
            return;
        }

        List<commerciale> commerciales = commercialeRepository
            .findAllByOrderByNomAscPrenomAscIdCommercialAsc();
        List<back_office> backOffices = backOfficeRepository
            .findAllByOrderByNomAscPrenomAscIdBackOfficeAsc();

        int targetedCreated = seedTargetedCommercialRequests(commerciales);
        int created = 0;
        for (int index = 1; index <= requestedCount; index++) {
            String email = buildEmail(index);
            if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
                continue;
            }

            LocalDate submissionDate = LocalDate.now().minusDays((index - 1) % 30L);
            StatusDossier status = resolveStatus(index);
            TypeAffiliation typeAffiliation = resolveAffiliationType(index);
            TypeCommercant typeCommercant = resolveMerchantType(index);

            utilisateur utilisateur = buildUtilisateur(email, status, submissionDate);
            utilisateur = utilisateurRepository.save(utilisateur);

            commercant commercant = buildCommercant(index, utilisateur, typeCommercant, typeAffiliation);
            commercant = commercantRepository.save(commercant);

            dossier_affiliation dossier = buildDossier(
                index,
                commercant,
                status,
                typeAffiliation,
                submissionDate,
                pickCommerciale(commerciales, index),
                pickBackOffice(backOffices, index)
            );
            dossierAffiliationRepository.save(dossier);
            created++;
        }

        LOGGER.info(
            "Affiliation demo seed completed: {} generic requests created out of {} requested, {} targeted commercial requests created.",
            created,
            requestedCount,
            targetedCreated
        );
    }

    private int seedTargetedCommercialRequests(List<commerciale> commerciales) {
        List<TargetedAffiliationSeed> seeds = List.of(
            new TargetedAffiliationSeed(
                9001,
                "demo.casa.affiliation@lanacash.local",
                "Casa Market Centre",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.TPE,
                TypeCommercant.PERSONNE_MORALE,
                "Commerce de detail",
                "Alimentation"
            ),
            new TargetedAffiliationSeed(
                9002,
                "demo.houceima.affiliation@lanacash.local",
                "Hoceima Services",
                "Al Hoceima",
                "Tanger-Tetouan-Al Hoceima",
                TypeAffiliation.SOFTPOS,
                TypeCommercant.PERSONNE_PHYSIQUE,
                "Services aux particuliers",
                "ServicesParticuliers"
            ),
            new TargetedAffiliationSeed(
                9003,
                "demo.beni.mellal.affiliation@lanacash.local",
                "Beni Mellal Distribution",
                "Beni Mellal",
                "Beni Mellal-Khenifra",
                TypeAffiliation.QR_CODE,
                TypeCommercant.AUTO_ENTREPRENEUR,
                "Commerce de gros",
                "AgricultureAgroalimentaire"
            ),
            new TargetedAffiliationSeed(
                9004,
                "demo.casa.ecommerce.affiliation@lanacash.local",
                "Casa Digital Store",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.E_COMMERCE,
                TypeCommercant.PERSONNE_MORALE,
                "IT",
                "TelecomNumerique"
            ),
            // 5 demandes Casablanca test – statuts variés
            new TargetedAffiliationSeed(
                9010,
                "test.casa.maarif@lanacash.local",
                "Boutique Maarif Fashion",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.TPE,
                TypeCommercant.PERSONNE_MORALE,
                "Prêt-à-porter et accessoires",
                "Textile",
                StatusDossier.SOUMIS
            ),
            new TargetedAffiliationSeed(
                9011,
                "test.casa.anfa.resto@lanacash.local",
                "Restaurant Anfa Prestige",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.SOFTPOS,
                TypeCommercant.PERSONNE_PHYSIQUE,
                "Restauration",
                "HotellerieRestauration",
                StatusDossier.CONTRAT_A_SIGNER
            ),
            new TargetedAffiliationSeed(
                9012,
                "test.casa.pharmacie.centreville@lanacash.local",
                "Pharmacie Centre Hassan II",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.QR_CODE,
                TypeCommercant.PERSONNE_MORALE,
                "Pharmacie et para-pharmacie",
                "Sante",
                StatusDossier.EN_ATTENTE_VALIDATION_BOA
            ),
            new TargetedAffiliationSeed(
                9013,
                "test.casa.aindiab.super@lanacash.local",
                "Supermarché Aïn Diab",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.TPE,
                TypeCommercant.PERSONNE_MORALE,
                "Grande distribution alimentaire",
                "Alimentation",
                StatusDossier.ACCEPTE
            ),
            new TargetedAffiliationSeed(
                9014,
                "test.casa.corniche.cafe@lanacash.local",
                "Café Corniche Atlantic",
                "Casablanca",
                "Casablanca-Settat",
                TypeAffiliation.E_COMMERCE,
                TypeCommercant.AUTO_ENTREPRENEUR,
                "Café et snacking",
                "HotellerieRestauration",
                StatusDossier.ABANDONNE
            )
        );

        int created = 0;
        for (TargetedAffiliationSeed seed : seeds) {
            if (utilisateurRepository.existsByEmailIgnoreCase(seed.email())) {
                continue;
            }

            LocalDate submissionDate = LocalDate.now().minusDays(created);
            StatusDossier seedStatus = seed.status();
            utilisateur utilisateur = buildUtilisateur(seed.email(), seedStatus, submissionDate);
            utilisateur = utilisateurRepository.save(utilisateur);

            commercant commercant = buildCommercant(
                seed.index(),
                utilisateur,
                seed.typeCommercant(),
                seed.typeAffiliation()
            );
            commercant.setNomCommercial(seed.nomCommercial());
            commercant.setRaisonSociale(seed.nomCommercial() + " SARL");
            commercant.setAdresse(seed.index() + " Avenue principale");
            commercant.setVille(seed.ville());
            commercant.setRegion(seed.region());
            commercant.setActivite(seed.activite());
            commercant.setSecteur(seed.secteur());
            commercant.setEmailContact(seed.email());
            commercant = commercantRepository.save(commercant);

            List<back_office> backOffices = backOfficeRepository
                .findAllByOrderByNomAscPrenomAscIdBackOfficeAsc();
            dossier_affiliation dossier = buildDossier(
                seed.index(),
                commercant,
                seedStatus,
                seed.typeAffiliation(),
                submissionDate,
                pickCommerciale(commerciales, created + 1),
                pickBackOffice(backOffices, created + 1)
            );
            dossier.setProspectStatus(ProspectStatus.NOUVEAU);
            dossier.setOrigineCreation("COMMERCIAL_DIRECT");
            dossierAffiliationRepository.save(dossier);
            created++;
        }

        if (created > 0) {
            LOGGER.info("Targeted commercial affiliation seed completed: {} requests created.", created);
        }
        return created;
    }

    private utilisateur buildUtilisateur(
        String email,
        StatusDossier status,
        LocalDate submissionDate
    ) {
        utilisateur utilisateur = new utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setPassword(passwordHashService.hash(defaultPassword));
        utilisateur.setRole(RoleUser.COMMERCANT);
        utilisateur.setTokenVersion(0);

        if (status == StatusDossier.ACCEPTE) {
            utilisateur.setActive(Boolean.TRUE);
            utilisateur.setDateActivation(submissionDate.plusDays(3));
            utilisateur.setPasswordExpiresAt(null);
        } else {
            utilisateur.setActive(Boolean.FALSE);
            utilisateur.setDateActivation(null);
            utilisateur.setPasswordExpiresAt(null);
        }

        return utilisateur;
    }

    private commercant buildCommercant(
        int index,
        utilisateur utilisateur,
        TypeCommercant typeCommercant,
        TypeAffiliation typeAffiliation
    ) {
        String suffix = "%02d".formatted(index);
        commercant commercant = new commercant();
        commercant.setUtilisateur(utilisateur);
        commercant.setType(typeCommercant);
        commercant.setNomCommercial(resolveTradeName(typeCommercant, suffix));
        commercant.setRaisonSociale("Demo Commerce " + suffix + " SARL");
        commercant.setRegistreCommerce("RC-DEMO-" + suffix);
        commercant.setIdentifiantFiscal("ICE-DEMO-" + String.format(Locale.ROOT, "%05d", index));
        commercant.setAdresse(index + " Avenue Mohammed V");
        commercant.setVille(resolveCity(index));
        commercant.setRegion(resolveRegion(index));
        commercant.setTelephone("060000" + String.format(Locale.ROOT, "%04d", index));
        commercant.setTelephoneSecondaire("070000" + String.format(Locale.ROOT, "%04d", index));
        commercant.setEmailContact(utilisateur.getEmail());
        commercant.setActivite(resolveActivity(typeAffiliation));
        commercant.setSecteur(resolveSector(typeAffiliation));
        commercant.setMcc("53" + String.format(Locale.ROOT, "%02d", (index % 90) + 10));
        commercant.setChainePointVente("Reseau demo " + ((index - 1) % 6 + 1));
        commercant.setNombrePointsVente((index % 4) + 1);
        if (utilisateur.getDateActivation() != null) {
            commercant.setDateAffiliation(utilisateur.getDateActivation());
        }
        return commercant;
    }

    private dossier_affiliation buildDossier(
        int index,
        commercant commercant,
        StatusDossier status,
        TypeAffiliation typeAffiliation,
        LocalDate submissionDate,
        commerciale commerciale,
        back_office backOffice
    ) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(status);
        dossier.setTypeAffiliation(typeAffiliation);
        dossier.setDateCreation(submissionDate.minusDays(1));
        dossier.setDateSoumission(submissionDate);
        dossier.setRib("011780000000" + String.format(Locale.ROOT, "%08d", index));

        if (status != StatusDossier.SOUMIS) {
            dossier.setCommerciale(commerciale);
        }

        if (status == StatusDossier.ACCEPTE || status == StatusDossier.ABANDONNE) {
            dossier.setBackOffice(backOffice);
            dossier.setDateTraitementBackOffice(submissionDate.plusDays(2));
        }

        if (status == StatusDossier.ABANDONNE) {
            dossier.setMotifRefus("Dossier de demonstration abandonné pour informations incompletes.");
        }

        applyTypeSpecificValues(dossier, typeAffiliation, index);
        return dossier;
    }

    private void applyTypeSpecificValues(
        dossier_affiliation dossier,
        TypeAffiliation typeAffiliation,
        int index
    ) {
        switch (typeAffiliation) {
            case E_COMMERCE -> {
                dossier.setModeServiceEcommerce(index % 2 == 0 ? "API" : "Page de paiement hebergee");
                dossier.setSiteMarchandUrl("https://demo-store-" + index + ".ma");
                dossier.setApplicationMobile(index % 3 == 0 ? "Oui" : "Non");
                dossier.setCommissionLocaleEcommerce("1.20%");
                dossier.setCommissionEtrangereEcommerce("2.40%");
                dossier.setFraisMiseEnServiceEcommerce("1500 MAD");
            }
            case SOFTPOS -> {
                dossier.setModeleQrSoftpos("SoftPOS Android");
                dossier.setCommissionLocaleQrSoftpos("0.85%");
                dossier.setCommissionEtrangereQrSoftpos("1.95%");
                dossier.setFraisServiceQrSoftpos("199 MAD / mois");
                dossier.setConditionsQrSoftpos("Terminal Android compatible NFC");
            }
            case QR_CODE -> {
                dossier.setModeleQrSoftpos("QR Code dynamique");
                dossier.setCommissionLocaleQrSoftpos("0.70%");
                dossier.setCommissionEtrangereQrSoftpos("1.80%");
                dossier.setFraisServiceQrSoftpos("99 MAD / mois");
                dossier.setConditionsQrSoftpos("Affichage QR en caisse et sur facture");
            }
            default -> {
            }
        }
    }

    private commerciale pickCommerciale(List<commerciale> commerciales, int index) {
        if (commerciales.isEmpty()) {
            return null;
        }

        return commerciales.get((index - 1) % commerciales.size());
    }

    private back_office pickBackOffice(List<back_office> backOffices, int index) {
        if (backOffices.isEmpty()) {
            return null;
        }

        return backOffices.get((index - 1) % backOffices.size());
    }

    private StatusDossier resolveStatus(int index) {
        int position = (index - 1) % 25;
        if (position < 10) {
            return StatusDossier.SOUMIS;
        }

        if (position < 16) {
            return StatusDossier.EN_ATTENTE_VALIDATION_BOA;
        }

        if (position < 22) {
            return StatusDossier.ACCEPTE;
        }

        return StatusDossier.ABANDONNE;
    }

    private TypeAffiliation resolveAffiliationType(int index) {
        return switch ((index - 1) % 3) {
            case 0 -> TypeAffiliation.E_COMMERCE;
            case 1 -> TypeAffiliation.SOFTPOS;
            default -> TypeAffiliation.QR_CODE;
        };
    }

    private TypeCommercant resolveMerchantType(int index) {
        return switch ((index - 1) % 4) {
            case 0 -> TypeCommercant.PERSONNE_MORALE;
            case 1 -> TypeCommercant.PERSONNE_PHYSIQUE;
            case 2 -> TypeCommercant.AUTO_ENTREPRENEUR;
            default -> TypeCommercant.ASSOCIATION_FONDATION;
        };
    }

    private String buildEmail(int index) {
        return emailPrefix
            + "+"
            + String.format(Locale.ROOT, "%02d", index)
            + "@lanacash.local";
    }

    private String resolveTradeName(TypeCommercant typeCommercant, String suffix) {
        return switch (typeCommercant) {
            case PERSONNE_PHYSIQUE -> "Boutique Atlas " + suffix;
            case AUTO_ENTREPRENEUR -> "Auto Entreprise Demo " + suffix;
            case ASSOCIATION_FONDATION -> "Association Demo " + suffix;
            default -> "Demo Commerce " + suffix;
        };
    }

    private String resolveActivity(TypeAffiliation typeAffiliation) {
        return switch (typeAffiliation) {
            case E_COMMERCE -> "Vente en ligne";
            case SOFTPOS -> "Encaissement mobile";
            case QR_CODE -> "Encaissement par QR Code";
            default -> "Commerce";
        };
    }

    private String resolveSector(TypeAffiliation typeAffiliation) {
        return switch (typeAffiliation) {
            case E_COMMERCE -> "Digital";
            case SOFTPOS -> "Retail";
            case QR_CODE -> "Services";
            default -> "Commerce";
        };
    }

    private String resolveCity(int index) {
        String[] cities = { "Casablanca", "Rabat", "Marrakech", "Tanger", "Agadir" };
        return cities[(index - 1) % cities.length];
    }

    private String resolveRegion(int index) {
        String[] regions = {
            "Casablanca-Settat",
            "Rabat-Sale-Kenitra",
            "Marrakech-Safi",
            "Tanger-Tetouan-Al Hoceima",
            "Souss-Massa"
        };
        return regions[(index - 1) % regions.length];
    }

    private record TargetedAffiliationSeed(
        int index,
        String email,
        String nomCommercial,
        String ville,
        String region,
        TypeAffiliation typeAffiliation,
        TypeCommercant typeCommercant,
        String activite,
        String secteur,
        StatusDossier status
    ) {
        TargetedAffiliationSeed(
            int index,
            String email,
            String nomCommercial,
            String ville,
            String region,
            TypeAffiliation typeAffiliation,
            TypeCommercant typeCommercant,
            String activite,
            String secteur
        ) {
            this(index, email, nomCommercial, ville, region, typeAffiliation, typeCommercant, activite, secteur, StatusDossier.BROUILLON);
        }
    }
}
