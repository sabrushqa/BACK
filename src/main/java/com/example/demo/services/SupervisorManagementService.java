package com.example.demo.services;

import com.example.demo.dto.AssignAffiliationRequest;
import com.example.demo.dto.CreateBackOfficeRequest;
import com.example.demo.dto.CreateCommercialeRequest;
import com.example.demo.dto.SupervisorActionResponse;
import com.example.demo.dto.SupervisorCommercantTransactionsResponse;
import com.example.demo.dto.SupervisorEcommerceSiteAssignRequest;
import com.example.demo.dto.SupervisorOverviewResponse;
import com.example.demo.dto.SupervisorPasswordChangeRequest;
import com.example.demo.dto.SupervisorPdvMapResponse;
import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.dto.SupervisorTpeStockResponse;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SupervisorManagementService {

    private final UtilisateurRepository utilisateurRepository;
    private final BackOfficeRepository backOfficeRepository;
    private final CommercialeRepository commercialeRepository;
    private final CommercantRepository commercantRepository;
    private final SwitchMonetiqueClient switchMonetiqueClient;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final PdvRepository pdvRepository;
    private final PasswordHashService passwordHashService;
    private final JwtService jwtService;
    private final ActivationMailService activationMailService;
    private final KeycloakAdminService keycloakAdminService;
    private final SupervisorNotificationService supervisorNotificationService;
    private final GeocodingService geocodingService;
    private final long activationExpirationMinutes;
    private final String frontendBaseUrl;
    private final LocalDate pdvMapCutoffDate;

    public SupervisorManagementService(
        UtilisateurRepository utilisateurRepository,
        BackOfficeRepository backOfficeRepository,
        CommercialeRepository commercialeRepository,
        CommercantRepository commercantRepository,
        SwitchMonetiqueClient switchMonetiqueClient,
        DossierAffiliationRepository dossierAffiliationRepository,
        PdvRepository pdvRepository,
        PasswordHashService passwordHashService,
        JwtService jwtService,
        ActivationMailService activationMailService,
        KeycloakAdminService keycloakAdminService,
        SupervisorNotificationService supervisorNotificationService,
        GeocodingService geocodingService,
        @Value("${app.auth.activation-expiration-minutes:60}") long activationExpirationMinutes,
        @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl,
        @Value("${app.pdv-map.cutoff-date:2026-07-16}") String pdvMapCutoffDate
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.backOfficeRepository = backOfficeRepository;
        this.commercialeRepository = commercialeRepository;
        this.commercantRepository = commercantRepository;
        this.switchMonetiqueClient = switchMonetiqueClient;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.pdvRepository = pdvRepository;
        this.passwordHashService = passwordHashService;
        this.jwtService = jwtService;
        this.activationMailService = activationMailService;
        this.keycloakAdminService = keycloakAdminService;
        this.supervisorNotificationService = supervisorNotificationService;
        this.geocodingService = geocodingService;
        this.activationExpirationMinutes = activationExpirationMinutes;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.pdvMapCutoffDate = LocalDate.parse(pdvMapCutoffDate);
    }

    @Transactional(readOnly = true)
    public SupervisorOverviewResponse getOverview(String authorizationHeader) {
        readAuthenticatedSupervisor(authorizationHeader);

        List<SupervisorOverviewResponse.BackOfficeItem> backOffices = backOfficeRepository
            .findAllByOrderByNomAscPrenomAscIdBackOfficeAsc()
            .stream()
            .map(this::mapBackOfficeItem)
            .toList();

        List<SupervisorOverviewResponse.CommercialeItem> commerciales = commercialeRepository
            .findAllByOrderByNomAscPrenomAscIdCommercialAsc()
            .stream()
            .map(this::mapCommercialeItem)
            .toList();

        Map<Long, String> typeAffiliationByCommercantIdForOverview = buildTypeAffiliationByCommercantId();

        List<SupervisorOverviewResponse.CommercantItem> commercants = commercantRepository
            .findAll()
            .stream()
            .sorted(
                Comparator
                    .comparing(
                        (commercant item) -> safe(firstNotBlank(item.getNomCommercial(), item.getRaisonSociale())),
                        String.CASE_INSENSITIVE_ORDER
                    )
                    .thenComparing(commercant::getIdCommercant)
            )
            .map(item -> mapCommercantItem(item, typeAffiliationByCommercantIdForOverview))
            .toList();

        return new SupervisorOverviewResponse(backOffices, commerciales, commercants);
    }

    @Transactional(readOnly = true)
    public SupervisorPdvMapResponse getPdvMap(String authorizationHeader) {
        readAuthenticatedSupervisor(authorizationHeader);

        Map<Long, String> typeAffiliationByPdvId = dossierAffiliationRepository
            .findAllByRequestedPdvIsNotNull()
            .stream()
            .filter(dossier -> dossier.getTypeAffiliation() != null)
            .collect(Collectors.toMap(
                dossier -> dossier.getRequestedPdv().getIdPDV(),
                dossier -> dossier.getTypeAffiliation().name(),
                (first, second) -> first
            ));

        Map<Long, String> typeAffiliationByCommercantId = buildTypeAffiliationByCommercantId();

        // Seuls les PDV crees a partir de la date de bascule vers le geocodage reel,
        // effectivement geolocalises (geocodage reussi a la creation) ET rattaches a
        // une affiliation deja active (statut ACTIF, positionne quand le dossier passe
        // ACCEPTE) sont affiches sur la carte - un PDV dont la demande est encore en
        // attente (EN_ATTENTE/EN_VERIFICATION) ne doit pas apparaitre.
        List<SupervisorPdvMapResponse.PdvMapItem> pdvs = pdvRepository
            .findAll()
            .stream()
            .filter(point -> point.getLatitude() != null && point.getLongitude() != null)
            .filter(point -> point.getDateCreation() != null && !point.getDateCreation().isBefore(pdvMapCutoffDate))
            .filter(point -> "ACTIF".equalsIgnoreCase(safe(point.getStatut())))
            .map(point -> mapPdvMapItem(point, typeAffiliationByPdvId, typeAffiliationByCommercantId))
            .toList();

        return new SupervisorPdvMapResponse(pdvs);
    }

    /**
     * Retente le géocodage des PDV qui n'ont jamais pu être positionnés (adresse
     * corrigée depuis, ou PDV créé avant l'ajout du géocodage). Appels séquentiels
     * avec une pause pour respecter la limite ~1 req/s de Nominatim.
     */
    public SupervisorActionResponse regeocoderPdvsExistants(String authorizationHeader) {
        readAuthenticatedSupervisor(authorizationHeader);

        List<pdv> aGeocoder = pdvRepository
            .findAll()
            .stream()
            .filter(point -> point.getLatitude() == null || point.getLongitude() == null)
            .filter(point -> StringUtils.hasText(point.getAdresse()) && StringUtils.hasText(point.getVille()))
            .filter(point -> point.getDateCreation() != null && !point.getDateCreation().isBefore(pdvMapCutoffDate))
            .toList();

        int reussis = 0;
        for (pdv point : aGeocoder) {
            Optional<GeocodingService.Coordonnees> coordonnees = geocodingService.geocoder(
                point.getAdresse(),
                point.getQuartier(),
                point.getVille(),
                point.getCodePostal()
            );
            if (coordonnees.isPresent()) {
                point.setLatitude(coordonnees.get().latitude());
                point.setLongitude(coordonnees.get().longitude());
                pdvRepository.save(point);
                reussis++;
            }
            try {
                Thread.sleep(1100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return new SupervisorActionResponse(
            reussis + " point(s) de vente géolocalisé(s) sur " + aGeocoder.size() + " tenté(s)."
        );
    }

    private Map<Long, String> buildTypeAffiliationByCommercantId() {
        return dossierAffiliationRepository
            .findAllByOrderByDateSoumissionDescIdDossierDesc()
            .stream()
            .filter(
                dossier ->
                    dossier.getCommercant() != null
                        && dossier.getTypeAffiliation() != null
                        && !"NOUVEAU_PDV".equalsIgnoreCase(safe(dossier.getOrigineCreation()))
            )
            .collect(Collectors.toMap(
                dossier -> dossier.getCommercant().getIdCommercant(),
                dossier -> dossier.getTypeAffiliation().name(),
                (mostRecent, older) -> mostRecent
            ));
    }

    private SupervisorPdvMapResponse.PdvMapItem mapPdvMapItem(
        pdv point,
        Map<Long, String> typeAffiliationByPdvId,
        Map<Long, String> typeAffiliationByCommercantId
    ) {
        commercant commercant = point.getCommercant();
        String typeAffiliation = typeAffiliationByPdvId.get(point.getIdPDV());
        if (typeAffiliation == null && commercant != null) {
            typeAffiliation = typeAffiliationByCommercantId.get(commercant.getIdCommercant());
        }

        return new SupervisorPdvMapResponse.PdvMapItem(
            point.getIdPDV(),
            safe(point.getNomPDV()),
            safe(point.getVille()),
            safe(point.getAdresse()),
            safe(point.getQuartier()),
            safe(point.getCodePostal()),
            point.getLatitude(),
            point.getLongitude(),
            safe(point.getStatut()),
            commercant == null ? "" : firstNotBlank(commercant.getNomCommercial(), commercant.getRaisonSociale()),
            commercant == null || commercant.getType() == null ? "" : commercant.getType().name(),
            safe(typeAffiliation),
            commercant == null ? "" : safe(commercant.getRegion())
        );
    }

    public SupervisorActionResponse createBackOffice(
        String authorizationHeader,
        CreateBackOfficeRequest request
    ) {
        readAuthenticatedSupervisor(authorizationHeader);

        requireText(request.nom(), "Le nom du back office est obligatoire.");
        requireText(request.prenom(), "Le prénom du back office est obligatoire.");
        requireText(request.email(), "L'e-mail du back office est obligatoire.");
        requireText(request.matricule(), "Le matricule du back office est obligatoire.");
        requireText(request.service(), "Le service du back office est obligatoire.");

        PreparedUserAccount preparedUser = prepareInactiveUser(request.email(), RoleUser.BACK_OFFICE);

        back_office backOffice = new back_office();
        backOffice.setNom(normalize(request.nom()));
        backOffice.setPrenom(normalize(request.prenom()));
        backOffice.setMatricule(normalize(request.matricule()));
        backOffice.setService(normalize(request.service()));
        backOffice.setPeutValiderDossiers(Boolean.TRUE.equals(request.peutValiderDossiers()));
        backOffice.setPeutAffecterTpe(Boolean.TRUE.equals(request.peutAffecterTpe()));
        backOffice.setPeutGererReclamations(Boolean.TRUE.equals(request.peutGererReclamations()));
        backOffice.setUtilisateur(preparedUser.utilisateur());
        backOfficeRepository.save(backOffice);
        if (!keycloakAdminService.provisionUser(preparedUser.utilisateur(), preparedUser.temporaryPassword())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le compte Keycloak n'a pas pu être préparé. Aucun e-mail d'activation n'a été envoyé."
            );
        }
        utilisateurRepository.save(preparedUser.utilisateur());

        ActivationMailService.MailDispatchResult dispatchResult =
            activationMailService.sendAccountSetupEmail(
                preparedUser.utilisateur(),
                buildDisplayName(request.prenom(), request.nom(), preparedUser.utilisateur().getEmail()),
                "back office",
                preparedUser.temporaryPassword()
            );

        return new SupervisorActionResponse(
            dispatchResult.sent()
                ? "Le compte back office a été créé. Un e-mail a été envoyé pour définir le mot de passe."
                : "Le compte back office a été créé. L'e-mail d'activation n'a pas pu etre envoyé automatiquement."
        );
    }

    public SupervisorActionResponse createCommerciale(
        String authorizationHeader,
        CreateCommercialeRequest request
    ) {
        readAuthenticatedSupervisor(authorizationHeader);

        requireText(request.nom(), "Le nom du commercial est obligatoire.");
        requireText(request.prenom(), "Le prénom du commercial est obligatoire.");
        requireText(request.email(), "L'e-mail du commercial est obligatoire.");
        requireText(request.matricule(), "Le matricule du commercial est obligatoire.");
        requireText(request.region(), "La region du commercial est obligatoire.");
        requireText(request.telephone(), "Le téléphone du commercial est obligatoire.");

        PreparedUserAccount preparedUser = prepareInactiveUser(request.email(), RoleUser.COMMERCIAL);

        commerciale commerciale = new commerciale();
        commerciale.setNom(normalize(request.nom()));
        commerciale.setPrenom(normalize(request.prenom()));
        commerciale.setMatricule(normalize(request.matricule()));
        commerciale.setRegion(normalize(request.region()));
        commerciale.setTelephone(normalize(request.telephone()));
        commerciale.setUtilisateur(preparedUser.utilisateur());
        commercialeRepository.save(commerciale);
        if (!keycloakAdminService.provisionUser(preparedUser.utilisateur(), preparedUser.temporaryPassword())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le compte Keycloak n'a pas pu être préparé. Aucun e-mail d'activation n'a été envoyé."
            );
        }
        utilisateurRepository.save(preparedUser.utilisateur());

        ActivationMailService.MailDispatchResult dispatchResult =
            activationMailService.sendAccountSetupEmail(
                preparedUser.utilisateur(),
                buildDisplayName(request.prenom(), request.nom(), preparedUser.utilisateur().getEmail()),
                "commercial",
                preparedUser.temporaryPassword()
            );

        return new SupervisorActionResponse(
            dispatchResult.sent()
                ? "Le compte commercial a été créé. Un e-mail a été envoyé pour définir le mot de passe."
                : "Le compte commercial a été créé. L'e-mail d'activation n'a pas pu etre envoyé automatiquement."
        );
    }

    public SupervisorActionResponse changePassword(
        String authorizationHeader,
        SupervisorPasswordChangeRequest request
    ) {
        utilisateur supervisor = readAuthenticatedSupervisor(authorizationHeader);
        boolean emailSent = keycloakAdminService.sendPasswordSetupEmail(
            supervisor,
            frontendBaseUrl + "/login"
        );

        if (!emailSent) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "L'e-mail de réinitialisation Keycloak n'a pas pu etre envoyé. Vérifiez la configuration SMTP Keycloak."
            );
        }

        return new SupervisorActionResponse(
            "Un e-mail Keycloak de réinitialisation du mot de passe a été envoyé."
        );
    }

    public SupervisorActionResponse deactivateBackOffice(String authorizationHeader, Long backOfficeId) {
        readAuthenticatedSupervisor(authorizationHeader);

        back_office backOffice = backOfficeRepository
            .findById(backOfficeId)
            .orElseThrow(() -> new IllegalArgumentException("Compte back office introuvable."));

        deactivateManagedUser(
            backOffice.getUtilisateur()
        );
        return new SupervisorActionResponse("Le compte back office a été desactive.");
    }

    public SupervisorActionResponse sendBackOfficeActivation(String authorizationHeader, Long backOfficeId) {
        readAuthenticatedSupervisor(authorizationHeader);

        back_office backOffice = backOfficeRepository
            .findById(backOfficeId)
            .orElseThrow(() -> new IllegalArgumentException("Compte back office introuvable."));

        utilisateur utilisateur = prepareManagedUserForActivation(
            backOffice.getUtilisateur(),
            RoleUser.BACK_OFFICE
        );
        String temporaryPassword = generateAndApplyTemporaryPassword(utilisateur);

        ActivationMailService.MailDispatchResult dispatchResult =
            activationMailService.sendAccountSetupEmail(
                utilisateur,
                buildDisplayName(backOffice.getPrenom(), backOffice.getNom(), utilisateur.getEmail()),
                "back office",
                temporaryPassword
            );

        return new SupervisorActionResponse(
            dispatchResult.sent()
                ? "Le compte back office a été réactivé. Un e-mail d'activation a été envoyé."
                : "Le compte back office a été préparé pour activation, mais l'e-mail n'a pas pu etre envoyé automatiquement."
        );
    }

    public SupervisorActionResponse deactivateCommerciale(String authorizationHeader, Long commercialeId) {
        readAuthenticatedSupervisor(authorizationHeader);

        commerciale commerciale = commercialeRepository
            .findById(commercialeId)
            .orElseThrow(() -> new IllegalArgumentException("Compte commercial introuvable."));

        deactivateManagedUser(
            commerciale.getUtilisateur()
        );
        return new SupervisorActionResponse("Le compte commercial a été desactive.");
    }

    public SupervisorActionResponse sendCommercialeActivation(String authorizationHeader, Long commercialeId) {
        readAuthenticatedSupervisor(authorizationHeader);

        commerciale commerciale = commercialeRepository
            .findById(commercialeId)
            .orElseThrow(() -> new IllegalArgumentException("Compte commercial introuvable."));

        utilisateur utilisateur = prepareManagedUserForActivation(
            commerciale.getUtilisateur(),
            RoleUser.COMMERCIAL
        );
        String temporaryPassword = generateAndApplyTemporaryPassword(utilisateur);

        ActivationMailService.MailDispatchResult dispatchResult =
            activationMailService.sendAccountSetupEmail(
                utilisateur,
                buildDisplayName(commerciale.getPrenom(), commerciale.getNom(), utilisateur.getEmail()),
                "commercial",
                temporaryPassword
            );

        return new SupervisorActionResponse(
            dispatchResult.sent()
                ? "Le compte commercial a été réactivé. Un e-mail d'activation a été envoyé."
                : "Le compte commercial a été préparé pour activation, mais l'e-mail n'a pas pu etre envoyé automatiquement."
        );
    }

    public SupervisorActionResponse deactivateCommercant(String authorizationHeader, Long commercantId) {
        readAuthenticatedSupervisor(authorizationHeader);

        commercant commercant = commercantRepository
            .findById(commercantId)
            .orElseThrow(() -> new IllegalArgumentException("Compte commerçant introuvable."));

        deactivateManagedUser(
            commercant.getUtilisateur()
        );
        return new SupervisorActionResponse("Le compte commerçant a été desactive.");
    }

    /**
     * Resilie un commercant deja affilie et actif (statut ACCEPTE) — distinct
     * d'une simple desactivation de compte (deactivateCommercant, qui peut
     * couvrir n'importe quelle raison : fraude, suspension temporaire...).
     * Ce statut RESILIE devient le vrai label metier "abandonne=1" pour un
     * futur export/reentrainement de lana-merchant-intelligence sur donnees
     * reelles — sans cette action, aucun vrai exemple positif n'existe.
     */
    public SupervisorActionResponse resilierCommercant(
        String authorizationHeader,
        Long commercantId,
        String motif
    ) {
        readAuthenticatedSupervisor(authorizationHeader);

        commercant commercant = commercantRepository
            .findById(commercantId)
            .orElseThrow(() -> new IllegalArgumentException("Compte commerçant introuvable."));

        dossier_affiliation dossier = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercantId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Aucun dossier d'affiliation pour ce commerçant."));

        if (dossier.getStatus() != StatusDossier.ACCEPTE) {
            throw new IllegalArgumentException(
                "Seul un commerçant actif (dossier accepté) peut être résilié."
            );
        }

        dossier.setStatus(StatusDossier.RESILIE);
        dossier.setMotifRefus(
            StringUtils.hasText(motif) ? motif : "Résiliation du contrat par le superviseur."
        );
        dossier.setDateTraitementBackOffice(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        deactivateManagedUser(commercant.getUtilisateur());

        return new SupervisorActionResponse("Le commerçant a été résilié.");
    }

    public SupervisorActionResponse sendCommercantActivation(String authorizationHeader, Long commercantId) {
        readAuthenticatedSupervisor(authorizationHeader);

        commercant commercant = commercantRepository
            .findById(commercantId)
            .orElseThrow(() -> new IllegalArgumentException("Compte commerçant introuvable."));

        utilisateur utilisateur = prepareManagedUserForActivation(
            commercant.getUtilisateur(),
            RoleUser.COMMERCANT
        );
        String temporaryPassword = generateAndApplyTemporaryPassword(utilisateur);

        ActivationMailService.MailDispatchResult dispatchResult =
            activationMailService.sendActivationEmail(utilisateur, commercant, temporaryPassword);

        return new SupervisorActionResponse(
            dispatchResult.sent()
                ? "Le compte commerçant a été réactivé. Un e-mail d'activation a été envoyé."
                : "Le compte commerçant a été préparé pour activation, mais l'e-mail n'a pas pu etre envoyé automatiquement."
        );
    }

    @Transactional(readOnly = true)
    public SupervisorTpeStockResponse getTpeStock(String authorizationHeader) {
        readAuthenticatedSupervisor(authorizationHeader);

        return new SupervisorTpeStockResponse(
            switchMonetiqueClient.stockComplet()
                .stream()
                .map(this::mapSwitchTpeStockItem)
                .toList()
        );
    }

    public SupervisorTpeStockResponse getEligibleTpesForDossier(String authorizationHeader, Long dossierId) {
        utilisateur authenticatedUser = readAuthenticatedBackOffice(authorizationHeader);
        requireTpeAssignmentPermission(authenticatedUser);

        dossier_affiliation dossier = dossierAffiliationRepository.findById(dossierId)
            .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable."));

        // Meme regle que validateTpeAssignment : pas de stock propose tant
        // que le contrat n'est pas reellement signe/depose (ACCEPTE) — sinon
        // le BOA voit un menu de TPE "disponibles" qu'il ne peut en realite
        // pas encore affecter, ce qui echouerait silencieusement au clic.
        if (dossier.getStatus() != StatusDossier.ACCEPTE || dossier.getTypeAffiliation() == TypeAffiliation.E_COMMERCE) {
            return new SupervisorTpeStockResponse(List.of());
        }

        String requiredType = resolveEffectiveTpeType(dossier);
        return new SupervisorTpeStockResponse(
            switchMonetiqueClient.stockDisponible(requiredType)
                .stream()
                .map(this::mapSwitchTpeStockItem)
                .toList()
        );
    }

    /**
     * Historique des transactions d'un commerçant, pour la page superviseur
     * "Transactions" (liste déroulante de commerçants). Même source de
     * vérité et même résolution de PDV que le dashboard commerçant
     * (MerchantAccessService::buildTransactionItemsForCommercant), mais sans
     * historique local — un superviseur n'a pas besoin des transactions
     * auto-provisionnées NOUVEAU_PDV, uniquement du flux réel côté switch.
     */
    public SupervisorCommercantTransactionsResponse getCommercantTransactions(
        String authorizationHeader,
        Long commercantId
    ) {
        readAuthenticatedSupervisor(authorizationHeader);

        commercant commercant = commercantRepository.findById(commercantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commerçant introuvable."));
        String commercantNom = firstNotBlankOrEmpty(commercant.getNomCommercial(), commercant.getRaisonSociale());

        List<pdv> pdvs = pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(commercantId);
        List<SwitchMonetiqueClient.SwitchTransaction> oracleTransactions = fetchOracleTransactionsSafely(commercantId);
        List<SwitchMonetiqueClient.SwitchTpe> oracleStock = fetchOracleStockSafelyForTransactions();

        Map<String, pdv> pdvById = pdvs.stream()
            .collect(Collectors.toMap(p -> p.getIdPDV().toString(), p -> p, (a, b) -> a));
        Map<String, String> pdvIdByTpeId = oracleStock.stream()
            .filter(tpe -> tpe.idPdv() != null)
            .collect(Collectors.toMap(
                SwitchMonetiqueClient.SwitchTpe::idTpe,
                SwitchMonetiqueClient.SwitchTpe::idPdv,
                (a, b) -> a
            ));

        List<com.example.demo.dto.MerchantSessionResponse.TransactionItem> items = oracleTransactions.stream()
            .map(transaction -> {
                String idPdv = transaction.idTpe() == null ? null : pdvIdByTpeId.get(transaction.idTpe());
                pdv matchedPdv = idPdv == null ? null : pdvById.get(idPdv);
                LocalDateTime date = transaction.dateTransaction();
                return new com.example.demo.dto.MerchantSessionResponse.TransactionItem(
                    transaction.idTransaction(),
                    safeOrEmpty(transaction.canal()),
                    date == null ? "" : date.toLocalDate().toString(),
                    date == null ? "" : date.toLocalTime().toString(),
                    transaction.montant(),
                    safeOrEmpty(transaction.devise()),
                    safeOrEmpty(transaction.statut()),
                    safeOrEmpty(transaction.mode()),
                    firstNotBlankOrEmpty(transaction.idTpe(), transaction.idSiteEcommerce()),
                    matchedPdv == null ? null : matchedPdv.getIdPDV(),
                    matchedPdv == null ? "" : safeOrEmpty(matchedPdv.getNomPDV())
                );
            })
            .sorted(Comparator.comparing((com.example.demo.dto.MerchantSessionResponse.TransactionItem item) ->
                item.dateTransaction() + "T" + item.heureTransaction()
            ).reversed())
            .toList();

        return new SupervisorCommercantTransactionsResponse(commercantId, commercantNom, items);
    }

    private List<SwitchMonetiqueClient.SwitchTransaction> fetchOracleTransactionsSafely(Long commercantId) {
        try {
            return switchMonetiqueClient.transactions(commercantId.toString());
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<SwitchMonetiqueClient.SwitchTpe> fetchOracleStockSafelyForTransactions() {
        try {
            return switchMonetiqueClient.stockComplet();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String firstNotBlankOrEmpty(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String safeOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public SupervisorActionResponse activateTpe(String authorizationHeader, String tpeId) {
        readAuthenticatedSupervisor(authorizationHeader);
        switchMonetiqueClient.activer(requireTpeId(tpeId));
        return new SupervisorActionResponse("La référence TPE a été activée.");
    }

    public SupervisorActionResponse deactivateTpe(String authorizationHeader, String tpeId) {
        readAuthenticatedSupervisor(authorizationHeader);
        switchMonetiqueClient.desactiver(requireTpeId(tpeId));
        return new SupervisorActionResponse("La référence TPE a été désactivée.");
    }

    public SupervisorActionResponse assignTpeToCommercant(
        String authorizationHeader,
        String tpeId,
        SupervisorTpeAssignRequest request
    ) {
        utilisateur authenticatedUser = readAuthenticatedBackOffice(authorizationHeader);
        requireTpeAssignmentPermission(authenticatedUser);
        SwitchMonetiqueClient.SwitchTpe terminal = switchMonetiqueClient.parId(requireTpeId(tpeId))
            .orElseThrow(() -> new IllegalArgumentException("Référence TPE introuvable."));
        if (!terminal.actif()) {
            throw new IllegalArgumentException("Cette référence TPE est inactive.");
        }

        if (request == null || request.dossierId() == null) {
            throw new IllegalArgumentException("Le dossier commerçant validé est obligatoire.");
        }

        if (terminal.idCommercant() != null) {
            throw new IllegalArgumentException("Cette référence TPE est déjà affectée à un commerçant.");
        }

        dossier_affiliation dossier = dossierAffiliationRepository.findById(request.dossierId())
            .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable."));
        validateTpeAssignment(terminal, dossier);

        commercant commercant = dossier.getCommercant();
        pdv pdvCible = dossier.getRequestedPdv();
        if (pdvCible == null) {
            List<pdv> pointVentes = pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(
                commercant.getIdCommercant()
            );
            if (pointVentes.isEmpty()) {
                throw new IllegalArgumentException(
                    "Aucun point de vente n'est lie a ce commerçant. Impossible d'affecter la référence TPE."
                );
            }
            pdvCible = pointVentes.get(0);
        }

        String nomCommercial = StringUtils.hasText(commercant.getNomCommercial())
            ? commercant.getNomCommercial()
            : commercant.getRaisonSociale();
        switchMonetiqueClient.affecter(
            terminal.idTpe(),
            commercant.getIdCommercant().toString(),
            pdvCible.getIdPDV().toString(),
            nomCommercial,
            dossier.getTypeAffiliation() == null ? null : dossier.getTypeAffiliation().name(),
            commercant.getRegion()
        );

        // Le prospect n'est marqué CONVERTI qu'ici : validé (ACCEPTE/CONTRAT_A_SIGNER)
        // + contrat signé (validateTpeAssignment l'exige déjà ci-dessus) + TPE
        // désormais réellement affecté. Avant cet appel, seul le contrat était
        // signé — le statut CONVERTI ne doit pas anticiper l'affectation TPE.
        if (isCommercialDirectDossier(dossier) && dossier.getProspectStatus() != ProspectStatus.CONVERTI) {
            dossier.setProspectStatus(ProspectStatus.CONVERTI);
            dossierAffiliationRepository.save(dossier);
        }

        supervisorNotificationService.notifyTpeAssigned(dossier, commercant);

        return new SupervisorActionResponse("La référence TPE a été affectée au commerçant du dossier validé.");
    }

    /**
     * Equivalent de assignTpeToCommercant() pour le canal e-commerce : un
     * dossier E_COMMERCE ne peut jamais recevoir de TPE (voir
     * validateTpeAssignment), il n'y avait donc jusqu'ici aucun evenement
     * capable de faire passer un prospect e-commerce en CONVERTI. Ici,
     * l'"affectation" equivalente est l'interfacage reel du site marchand
     * avec switch-monetique-service (creation de la fiche site_ecommerce
     * cote switch, liee au commercant).
     *
     * Contrairement au TPE, il n'y a pas de stock a choisir : l'identifiant
     * n'est jamais fourni par l'appelant, il est genere par switch-monetique-
     * service (source de verite du canal e-commerce) au moment du
     * provisionnement — le BOA ne fait que confirmer l'URL du site.
     */
    public SupervisorActionResponse assignEcommerceSiteToCommercant(
        String authorizationHeader,
        SupervisorEcommerceSiteAssignRequest request
    ) {
        utilisateur authenticatedUser = readAuthenticatedBackOffice(authorizationHeader);
        requireTpeAssignmentPermission(authenticatedUser);

        if (request == null || request.dossierId() == null) {
            throw new IllegalArgumentException("Le dossier commerçant validé est obligatoire.");
        }

        dossier_affiliation dossier = dossierAffiliationRepository.findById(request.dossierId())
            .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable."));

        // ENCAISSEMENT_ET_ECOMMERCE combine les deux canaux : ce dossier doit
        // pouvoir affecter un site e-commerce ICI ET une reference TPE via
        // assignTpeToCommercant (autorise pour tout type sauf E_COMMERCE pur,
        // voir validateTpeAssignment) — les deux affectations sont necessaires
        // en parallele, pas l'une a la place de l'autre.
        if (dossier.getTypeAffiliation() != TypeAffiliation.E_COMMERCE
            && dossier.getTypeAffiliation() != TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE) {
            throw new IllegalArgumentException("Ce dossier n'a pas de canal e-commerce à affecter.");
        }
        // Meme regle que validateTpeAssignment : contrat reellement signe/depose
        // avant toute affectation.
        if (dossier.getStatus() != StatusDossier.ACCEPTE) {
            throw new IllegalArgumentException(
                "Le contrat doit être signé et déposé par le commerçant avant de pouvoir affecter un site e-commerce."
            );
        }
        if (StringUtils.hasText(dossier.getIdSiteEcommerceAffecte())) {
            throw new IllegalArgumentException("Un site e-commerce est déjà affecté à ce dossier.");
        }

        commercant commercant = dossier.getCommercant();
        if (commercant == null || commercant.getIdCommercant() == null) {
            throw new IllegalArgumentException("Le commerçant du dossier est introuvable.");
        }

        String url = StringUtils.hasText(request.url()) ? request.url().trim() : dossier.getSiteMarchandUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("L'URL du site e-commerce est obligatoire.");
        }

        SwitchMonetiqueClient.SwitchSiteEcommerce site =
            switchMonetiqueClient.provisionnerSiteEcommerce(commercant.getIdCommercant().toString(), url);

        dossier.setIdSiteEcommerceAffecte(site.idSiteEcommerce());

        // Meme logique que pour le TPE : le prospect n'est marque CONVERTI
        // qu'une fois le canal reellement interface avec Switch, pas des la
        // simple validation du dossier (ACCEPTE).
        if (isCommercialDirectDossier(dossier) && dossier.getProspectStatus() != ProspectStatus.CONVERTI) {
            dossier.setProspectStatus(ProspectStatus.CONVERTI);
        }
        dossierAffiliationRepository.save(dossier);

        supervisorNotificationService.notifyEcommerceSiteAssigned(dossier, commercant);

        return new SupervisorActionResponse(
            "Le site e-commerce " + site.idSiteEcommerce() + " a été affecté au commerçant du dossier validé."
        );
    }

    private boolean isCommercialDirectDossier(dossier_affiliation dossier) {
        return "COMMERCIAL_DIRECT".equalsIgnoreCase(
            dossier.getOrigineCreation() == null ? null : dossier.getOrigineCreation().trim()
        );
    }

    public SupervisorActionResponse assignAffiliationToCommerciale(
        String authorizationHeader,
        Long dossierId,
        AssignAffiliationRequest request
    ) {
        readAuthenticatedSupervisor(authorizationHeader);

        if (request == null || request.commercialeId() == null) {
            throw new IllegalArgumentException("Le commercial à assigner est obligatoire.");
        }

        dossier_affiliation dossier = dossierAffiliationRepository.findById(dossierId)
            .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable."));

        if (dossier.getStatus() != StatusDossier.EN_ATTENTE_ASSIGNATION) {
            throw new IllegalArgumentException("Ce dossier n'est pas en attente d'assignation.");
        }

        commerciale commerciale = commercialeRepository.findById(request.commercialeId())
            .orElseThrow(() -> new IllegalArgumentException("Commercial introuvable."));

        commercant commercant = dossier.getCommercant();
        String dossierRegion = normalizeForRegionMatch(commercant == null ? null : commercant.getRegion());
        String commercialRegion = normalizeForRegionMatch(commerciale.getRegion());
        if (
            !StringUtils.hasText(dossierRegion)
                || !StringUtils.hasText(commercialRegion)
                || !dossierRegion.equals(commercialRegion)
        ) {
            throw new IllegalArgumentException(
                "Ce commercial n'appartient pas à la région du commerçant."
            );
        }

        dossier.setCommercialeAssignee(commerciale);
        dossier.setDateAssignationCommerciale(LocalDate.now());
        dossier.setStatus(StatusDossier.SOUMIS);
        dossierAffiliationRepository.save(dossier);

        supervisorNotificationService.notifyAssignedToCommercial(dossier, commercant, commerciale);

        return new SupervisorActionResponse("Le dossier a été assigné au commercial sélectionné.");
    }

    private String normalizeForRegionMatch(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private SupervisorOverviewResponse.BackOfficeItem mapBackOfficeItem(back_office backOffice) {
        utilisateur utilisateur = backOffice.getUtilisateur();

        return new SupervisorOverviewResponse.BackOfficeItem(
            backOffice.getIdBackOffice(),
            utilisateur == null ? null : utilisateur.getId(),
            safe(backOffice.getNom()),
            safe(backOffice.getPrenom()),
            utilisateur == null ? "" : safe(utilisateur.getEmail()),
            safe(backOffice.getMatricule()),
            safe(backOffice.getService()),
            utilisateur == null || utilisateur.getRole() == null ? "" : utilisateur.getRole().name(),
            utilisateur != null && Boolean.TRUE.equals(utilisateur.getActive()),
            utilisateur == null ? null : utilisateur.getDateCreation(),
            utilisateur == null ? null : utilisateur.getDateActivation()
        );
    }

    private SupervisorOverviewResponse.CommercantItem mapCommercantItem(
        commercant commercant,
        Map<Long, String> typeAffiliationByCommercantId
    ) {
        utilisateur utilisateur = commercant.getUtilisateur();
        String displayName = firstNotBlank(
            commercant.getNomCommercial(),
            commercant.getRaisonSociale(),
            utilisateur == null ? "" : utilisateur.getEmail()
        );

        return new SupervisorOverviewResponse.CommercantItem(
            commercant.getIdCommercant(),
            utilisateur == null ? null : utilisateur.getId(),
            displayName,
            utilisateur == null ? safe(commercant.getEmailContact()) : safe(utilisateur.getEmail()),
            commercant.getType() == null ? "" : commercant.getType().name(),
            safe(typeAffiliationByCommercantId.get(commercant.getIdCommercant())),
            safe(commercant.getActivite()),
            safe(commercant.getVille()),
            safe(commercant.getRegion()),
            safe(firstNotBlank(commercant.getTelephone(), commercant.getTelephoneSecondaire())),
            utilisateur != null && Boolean.TRUE.equals(utilisateur.getActive()),
            utilisateur == null ? null : utilisateur.getDateCreation(),
            utilisateur == null ? null : utilisateur.getDateActivation()
        );
    }

    private SupervisorOverviewResponse.CommercialeItem mapCommercialeItem(commerciale commerciale) {
        utilisateur utilisateur = commerciale.getUtilisateur();

        return new SupervisorOverviewResponse.CommercialeItem(
            commerciale.getIdCommercial(),
            utilisateur == null ? null : utilisateur.getId(),
            safe(commerciale.getNom()),
            safe(commerciale.getPrenom()),
            utilisateur == null ? "" : safe(utilisateur.getEmail()),
            safe(commerciale.getMatricule()),
            safe(commerciale.getRegion()),
            safe(commerciale.getTelephone()),
            utilisateur == null || utilisateur.getRole() == null ? "" : utilisateur.getRole().name(),
            utilisateur != null && Boolean.TRUE.equals(utilisateur.getActive()),
            utilisateur == null ? null : utilisateur.getDateCreation(),
            utilisateur == null ? null : utilisateur.getDateActivation()
        );
    }

    private PreparedUserAccount prepareInactiveUser(String email, RoleUser role) {
        String normalizedEmail = normalizeEmail(email);
        if (utilisateurRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Un utilisateur avec cet e-mail existe déjà.");
        }

        String temporaryPassword = generateTemporaryPassword();

        utilisateur utilisateur = new utilisateur();
        utilisateur.setEmail(normalizedEmail);
        utilisateur.setPassword(null);
        utilisateur.setRole(role);
        utilisateur.setActive(Boolean.FALSE);
        utilisateur.setDateActivation(null);
        utilisateur.setPasswordExpiresAt(
            LocalDateTime.now().plusMinutes(activationExpirationMinutes)
        );
        utilisateur.setTokenVersion(0);
        clearPendingAuthentication(utilisateur);

        return new PreparedUserAccount(
            utilisateurRepository.save(utilisateur),
            temporaryPassword
        );
    }

    private utilisateur readAuthenticatedSupervisor(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification Keycloak requise.")
            );

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak invalidee.");
        }

        if (utilisateur.getRole() != RoleUser.SUPERVISEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès superviseur requis.");
        }

        if (!Boolean.TRUE.equals(utilisateur.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte superviseur n'est pas actif.");
        }

        return utilisateur;
    }

    private utilisateur readAuthenticatedBackOffice(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification Keycloak requise.")
            );

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak invalidee.");
        }

        if (utilisateur.getRole() != RoleUser.BACK_OFFICE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès back-office requis.");
        }

        if (!Boolean.TRUE.equals(utilisateur.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte n'est pas actif.");
        }

        return utilisateur;
    }

    private void requireTpeAssignmentPermission(utilisateur authenticatedUser) {
        // Tout agent BACK_OFFICE a acces complet a l'affectation des references TPE :
        // la restriction par permission individuelle (peutAffecterTpe) a ete supprimee.
    }

    private void clearPendingAuthentication(utilisateur utilisateur) {
        utilisateur.setLoginOtpChallengeId(null);
        utilisateur.setLoginOtpCodeHash(null);
        utilisateur.setLoginOtpExpiresAt(null);
        utilisateur.setLoginOtpFailedAttempts(null);
        utilisateur.setPasswordResetCodeHash(null);
        utilisateur.setPasswordResetExpiresAt(null);
        utilisateur.setPasswordResetFailedAttempts(null);
    }

    private void deactivateManagedUser(utilisateur utilisateur) {
        if (utilisateur == null) {
            throw new IllegalArgumentException("Le compte utilisateur lie est introuvable.");
        }

        if (!Boolean.TRUE.equals(utilisateur.getActive())) {
            throw new IllegalArgumentException("Ce compte est déjà desactive.");
        }

        utilisateur.setActive(Boolean.FALSE);
        utilisateur.setDateDesactivation(LocalDate.now());
        utilisateur.setTokenVersion(resolveTokenVersion(utilisateur) + 1);
        clearPendingAuthentication(utilisateur);
        utilisateurRepository.save(utilisateur);
        keycloakAdminService.disableUser(utilisateur);
    }

    private utilisateur prepareManagedUserForActivation(
        utilisateur utilisateur,
        RoleUser expectedRole
    ) {
        if (utilisateur == null) {
            throw new IllegalArgumentException("Le compte utilisateur lie est introuvable.");
        }

        if (utilisateur.getRole() != expectedRole) {
            throw new IllegalArgumentException("Le type du compte utilisateur ne correspond pas.");
        }

        return utilisateur;
    }

    private String generateAndApplyTemporaryPassword(utilisateur utilisateur) {
        String temporaryPassword = generateTemporaryPassword();
        utilisateur.setPassword(null);
        utilisateur.setActive(Boolean.FALSE);
        utilisateur.setDateActivation(null);
        utilisateur.setDateDesactivation(null);
        utilisateur.setPasswordExpiresAt(
            LocalDateTime.now().plusMinutes(activationExpirationMinutes)
        );
        utilisateur.setTokenVersion(resolveTokenVersion(utilisateur) + 1);
        clearPendingAuthentication(utilisateur);
        utilisateurRepository.save(utilisateur);
        if (!keycloakAdminService.provisionUser(utilisateur, temporaryPassword)) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le compte Keycloak n'a pas pu être préparé. Aucun e-mail d'activation n'a été envoyé."
            );
        }
        utilisateurRepository.save(utilisateur);
        return temporaryPassword;
    }

    private String normalizeEmail(String value) {
        // Sonar S2259 : normalize() peut renvoyer null (valeur d'entree null) ;
        // enchainer .toLowerCase() dessus sans garde levait alors une NPE au lieu
        // de propager proprement l'absence de valeur.
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String requireTpeId(String tpeId) {
        if (!StringUtils.hasText(tpeId)) {
            throw new IllegalArgumentException("La référence TPE est obligatoire.");
        }
        return tpeId;
    }

    /**
     * Resout le produit d'encaissement reel du dossier ("TPE"/"SOFTPOS"/"QR_CODE"),
     * y compris pour ENCAISSEMENT_ET_ECOMMERCE ou aucun typeCompatible de stock ne
     * correspond directement au nom de l'enum - meme heuristique que pour la
     * generation des contrats combines (modeMiseADispositionTpe rempli -> TPE,
     * sinon modeleQrSoftpos contient "QR" -> QR_CODE, sinon SOFTPOS).
     */
    private String resolveEffectiveTpeType(dossier_affiliation dossier) {
        TypeAffiliation typeAffiliation = dossier.getTypeAffiliation();
        if (typeAffiliation == TypeAffiliation.TPE || typeAffiliation == TypeAffiliation.SOFTPOS
            || typeAffiliation == TypeAffiliation.QR_CODE) {
            return typeAffiliation.name();
        }
        if (StringUtils.hasText(dossier.getModeMiseADispositionTpe())) {
            return "TPE";
        }
        String modeleQrSoftpos = safe(dossier.getModeleQrSoftpos()).toUpperCase(Locale.ROOT);
        if (modeleQrSoftpos.contains("QR")) {
            return "QR_CODE";
        }
        return "SOFTPOS";
    }

    private void validateTpeAssignment(SwitchMonetiqueClient.SwitchTpe terminal, dossier_affiliation dossier) {
        // CONTRAT_A_SIGNER = contrat genere, envoye au commerçant, mais PAS
        // ENCORE signe/depose (c'est le depot du contrat signe qui declenche
        // finalizeAutomaticAcceptance -> ACCEPTE). Le BOA ne doit pouvoir
        // affecter un TPE qu'une fois le contrat reellement signe — avant,
        // l'affectation etait acceptee a tort des la generation du contrat.
        if (dossier.getStatus() != StatusDossier.ACCEPTE) {
            throw new IllegalArgumentException(
                "Le contrat doit être signé et déposé par le commerçant avant de pouvoir affecter un TPE."
            );
        }

        commercant commercant = dossier.getCommercant();
        if (commercant == null || commercant.getIdCommercant() == null) {
            throw new IllegalArgumentException("Le commerçant du dossier est introuvable.");
        }

        TypeAffiliation typeAffiliation = dossier.getTypeAffiliation();
        if (typeAffiliation == null) {
            throw new IllegalArgumentException("Le type d'affiliation du dossier est introuvable.");
        }

        if (typeAffiliation == TypeAffiliation.E_COMMERCE) {
            throw new IllegalArgumentException("Un dossier e-commerce ne peut pas recevoir une référence TPE/QR/SoftPOS.");
        }

        String requiredType = resolveEffectiveTpeType(dossier);
        String terminalType = safe(terminal.nature());
        if (!requiredType.equals(terminalType)) {
            throw new IllegalArgumentException(
                "Référence incompatible: le dossier est "
                    + requiredType
                    + " mais la référence est "
                    + terminalType
                    + "."
            );
        }

        int requestedCount = resolveRequestedTpeCount(dossier);
        String commercantId = commercant.getIdCommercant().toString();
        // Une demande d'extension (NOUVEAU_PDV) vise un point de vente precis
        // (requestedPdv) : seul ce PDV doit compter, pas le total du
        // commercant — sinon un commercant deja titulaire d'un TPE sur son
        // PREMIER point de vente se voit refuser l'affectation sur une
        // NOUVELLE extension alors qu'aucun TPE n'a encore ete affecte sur le
        // PDV vise par celle-ci. Meme correctif que
        // StaffAffiliationManagementService::isTpeAlreadyFullyAssigned (bug
        // reel constate manuellement sur le meme scenario, compte "soraya").
        pdv requestedPdv = dossier.getRequestedPdv();
        long assignedCount;
        if (requestedPdv != null && requestedPdv.getIdPDV() != null) {
            String requestedPdvId = requestedPdv.getIdPDV().toString();
            assignedCount = switchMonetiqueClient.stockComplet()
                .stream()
                .filter(candidate -> requestedPdvId.equals(candidate.idPdv()))
                .count();
        } else {
            assignedCount = switchMonetiqueClient.stockComplet()
                .stream()
                .filter(candidate -> commercantId.equals(candidate.idCommercant()))
                .count();
        }
        if (assignedCount >= requestedCount) {
            throw new IllegalArgumentException(
                "Le nombre de références affectées au commerçant atteint déjà le nombre demandé dans le dossier ("
                    + requestedCount
                    + ")."
            );
        }
    }

    private int resolveRequestedTpeCount(dossier_affiliation dossier) {
        Integer requestedCount = dossier.getNombreTpe();
        if (requestedCount != null && requestedCount > 0) {
            return requestedCount;
        }

        if (dossier.getTypeAffiliation() == TypeAffiliation.TPE) {
            throw new IllegalArgumentException("Le nombre de TPE demandé dans le dossier est obligatoire.");
        }

        return 1;
    }

    private SupervisorTpeStockResponse.TpeStockItem mapSwitchTpeStockItem(SwitchMonetiqueClient.SwitchTpe terminal) {
        Long commercantId = parseLongOrNull(terminal.idCommercant());
        Long pdvId = parseLongOrNull(terminal.idPdv());
        commercant commercant = commercantId == null ? null : commercantRepository.findById(commercantId).orElse(null);
        pdv pointVente = pdvId == null ? null : pdvRepository.findById(pdvId).orElse(null);
        return new SupervisorTpeStockResponse.TpeStockItem(
            terminal.idTpe(),
            terminal.idTpe(),
            "",
            safe(terminal.nature()),
            safe(terminal.connectivite()),
            terminal.idCommercant() == null ? "DISPONIBLE" : "AFFECTE_COMMERCANT",
            terminal.actif(),
            "",
            null,
            commercant == null ? "" : firstNotBlank(
                commercant.getNomCommercial(),
                commercant.getRaisonSociale(),
                commercant.getEmailContact()
            ),
            commercantId,
            pointVente == null ? "" : safe(pointVente.getNomPDV()),
            null,
            terminal.dateCreation() == null ? null : terminal.dateCreation().toLocalDate()
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "http://localhost:4200";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private Long parseLongOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String buildDisplayName(String prenom, String nom, String fallbackEmail) {
        String fullName = (safe(prenom) + " " + safe(nom)).trim();
        return StringUtils.hasText(fullName) ? fullName : safe(fallbackEmail);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }

    private int resolveTokenVersion(utilisateur utilisateur) {
        return utilisateur.getTokenVersion() == null ? 0 : utilisateur.getTokenVersion();
    }

    private record PreparedUserAccount(utilisateur utilisateur, String temporaryPassword) {
    }
}
