package com.example.demo.services;

import com.example.demo.dto.AffiliationActionResponse;
import com.example.demo.dto.AffiliationActivationRequest;
import com.example.demo.dto.AffiliationAbandonRequest;
import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.dto.CommercialInteractionRequest;
import com.example.demo.dto.CommercialInteractionResponse;
import com.example.demo.dto.CommercialAffiliationDraftRequest;
import com.example.demo.dto.StaffAffiliationOverviewResponse;
import com.example.demo.entities.AE;
import com.example.demo.entities.Association;
import com.example.demo.entities.PM;
import com.example.demo.entities.PP;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.documents;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.interaction_commerciale;
import com.example.demo.entities.notifications;
import com.example.demo.entities.pdv;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusContrat;
import com.example.demo.enums.StatusDocument;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.enums.TypeContrat;
import com.example.demo.enums.TypeDocument;
import com.example.demo.enums.TypeInteraction;
import com.example.demo.enums.TypeNotification;
import com.example.demo.entities.compte_rendu;
import com.example.demo.entities.contrat;
import com.example.demo.repositories.AERepository;
import com.example.demo.repositories.AssociationRepository;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CompteRenduRepository;
import com.example.demo.repositories.ContratRepository;
import com.example.demo.repositories.DocumentsRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.InteractionCommercialeRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.PMRepository;
import com.example.demo.repositories.PPRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.example.demo.config.DocumentMimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StaffAffiliationManagementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffAffiliationManagementService.class);
    private static final int MAX_POINTS_VENTE = 10;
    private static final int MAX_TPE = 10;

    private final UtilisateurRepository utilisateurRepository;
    private final CommercialeRepository commercialeRepository;
    private final BackOfficeRepository backOfficeRepository;
    private final CommercantRepository commercantRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final InteractionCommercialeRepository interactionCommercialeRepository;
    private final DocumentsRepository documentsRepository;
    private final ContratRepository contratRepository;
    private final CompteRenduRepository compteRenduRepository;
    private final NotificationsRepository notificationsRepository;
    private final PPRepository ppRepository;
    private final PMRepository pmRepository;
    private final AERepository aeRepository;
    private final AssociationRepository associationRepository;
    private final PdvRepository pdvRepository;
    private final TpeRepository tpeRepository;
    private final SwitchMonetiqueClient switchMonetiqueClient;
    private final PasswordHashService passwordHashService;
    private final ActivationMailService activationMailService;
    private final AffiliationStatusMailService affiliationStatusMailService;
    private final ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;
    private final JwtService jwtService;
    private final KeycloakAdminService keycloakAdminService;
    private final DocumentMimeValidator documentMimeValidator;
    private final PdvGeocodingService pdvGeocodingService;
    @Autowired(required = false)
    private GoogleCalendarService googleCalendarService;
    @Autowired(required = false)
    private MicrosoftCalendarService microsoftCalendarService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long activationExpirationMinutes;
    private final Path uploadRoot;

    public StaffAffiliationManagementService(
        UtilisateurRepository utilisateurRepository,
        CommercialeRepository commercialeRepository,
        BackOfficeRepository backOfficeRepository,
        CommercantRepository commercantRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        InteractionCommercialeRepository interactionCommercialeRepository,
        DocumentsRepository documentsRepository,
        NotificationsRepository notificationsRepository,
        PPRepository ppRepository,
        PMRepository pmRepository,
        AERepository aeRepository,
        AssociationRepository associationRepository,
        PdvRepository pdvRepository,
        TpeRepository tpeRepository,
        SwitchMonetiqueClient switchMonetiqueClient,
        ContratRepository contratRepository,
        CompteRenduRepository compteRenduRepository,
        PasswordHashService passwordHashService,
        ActivationMailService activationMailService,
        AffiliationStatusMailService affiliationStatusMailService,
        ServiceDocumentContratAffiliation serviceDocumentContratAffiliation,
        JwtService jwtService,
        KeycloakAdminService keycloakAdminService,
        DocumentMimeValidator documentMimeValidator,
        PdvGeocodingService pdvGeocodingService,
        @Value("${app.auth.activation-expiration-minutes:60}") long activationExpirationMinutes,
        @Value("${app.affiliation.upload-dir:uploads/affiliations}") String uploadDirectory
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercialeRepository = commercialeRepository;
        this.backOfficeRepository = backOfficeRepository;
        this.commercantRepository = commercantRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.interactionCommercialeRepository = interactionCommercialeRepository;
        this.documentsRepository = documentsRepository;
        this.notificationsRepository = notificationsRepository;
        this.ppRepository = ppRepository;
        this.pmRepository = pmRepository;
        this.aeRepository = aeRepository;
        this.associationRepository = associationRepository;
        this.pdvRepository = pdvRepository;
        this.tpeRepository = tpeRepository;
        this.switchMonetiqueClient = switchMonetiqueClient;
        this.contratRepository = contratRepository;
        this.compteRenduRepository = compteRenduRepository;
        this.passwordHashService = passwordHashService;
        this.activationMailService = activationMailService;
        this.affiliationStatusMailService = affiliationStatusMailService;
        this.serviceDocumentContratAffiliation = serviceDocumentContratAffiliation;
        this.jwtService = jwtService;
        this.keycloakAdminService = keycloakAdminService;
        this.documentMimeValidator = documentMimeValidator;
        this.pdvGeocodingService = pdvGeocodingService;
        this.activationExpirationMinutes = activationExpirationMinutes;
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public StaffAffiliationOverviewResponse getRequests(String authorizationHeader) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );

        final commerciale authenticatedCommerciale = authenticatedUser.getRole() == RoleUser.COMMERCIAL
            ? commercialeRepository
                .findByUtilisateur_Id(authenticatedUser.getId())
                .orElse(null)
            : null;
        final Long authenticatedCommercialeId =
            authenticatedCommerciale == null ? null : authenticatedCommerciale.getIdCommercial();
        final back_office authenticatedBackOffice = authenticatedUser.getRole() == RoleUser.BACK_OFFICE
            ? backOfficeRepository
                .findByUtilisateur_Id(authenticatedUser.getId())
                .orElse(null)
            : null;

        if (authenticatedUser.getRole() == RoleUser.BACK_OFFICE && authenticatedBackOffice == null) {
            return new StaffAffiliationOverviewResponse(List.of());
        }

        List<StaffAffiliationOverviewResponse.AffiliationRequestItem> requests = dossierAffiliationRepository
            .findAllByOrderByDateSoumissionDescIdDossierDesc()
            .stream()
            .filter(
                dossier ->
                    authenticatedUser.getRole() != RoleUser.BACK_OFFICE
                        || isBackOfficeVisibleRequest(dossier, authenticatedBackOffice)
            )
            .filter(
                dossier -> {
                    if (authenticatedUser.getRole() != RoleUser.COMMERCIAL) {
                        return true;
                    }
                    if (isNewPdvProductRequest(dossier)) {
                        return isExtensionOwnedByCommercial(dossier, authenticatedCommerciale);
                    }
                    if (!isCommercialDirectDossier(dossier)) {
                        // Les demandes d'auto-affiliation ne sont visibles qu'une fois
                        // explicitement assignees par le superviseur au commercial concerne.
                        return dossier.getCommercialeAssignee() != null
                            && authenticatedCommercialeId != null
                            && authenticatedCommercialeId.equals(dossier.getCommercialeAssignee().getIdCommercial());
                    }
                    // Les dossiers crees directement par un commercial ne sont visibles que par leur auteur.
                    return dossier.getCommerciale() != null
                        && authenticatedCommercialeId != null
                        && authenticatedCommercialeId.equals(dossier.getCommerciale().getIdCommercial());
                }
            )
            .sorted(
                Comparator
                    .comparing(
                        (dossier_affiliation dossier) -> isAccountActive(dossier),
                        Comparator.naturalOrder()
                    )
                    .thenComparing(
                        dossier_affiliation::getDateSoumission,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
                    .thenComparing(
                        dossier_affiliation::getIdDossier,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
            )
            .map(this::mapRequestItem)
            .toList();

        return new StaffAffiliationOverviewResponse(requests);
    }

    public AffiliationActionResponse completeMerchantDossier(
        String authorizationHeader,
        Long dossierId,
        AffiliationActivationRequest request
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.COMMERCIAL
        );
        commerciale authenticatedCommerciale = commercialeRepository
            .findByUtilisateur_Id(authenticatedUser.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte commercial introuvable."
                )
            );

        dossier_affiliation dossier = readDossier(dossierId);
        validateCompletionEligibility(dossier);
        validateCommercialDirectOwnership(dossier, authenticatedCommerciale);
        validateAutoAffiliationAssignment(dossier, authenticatedCommerciale);

        boolean newPdvRequest = isNewPdvProductRequest(dossier);
        // Commerciale doit être positionné avant applyCommercialReportFields
        // pour que l'entité compte_rendu puisse récupérer la FK commerciale.
        dossier.setCommerciale(authenticatedCommerciale);
        applyNegotiableFields(dossier, request);
        applyMerchantMcc(dossier, request);
        applyCommercialReportFields(dossier, request);
        if (!newPdvRequest) {
            dossier.setBackOffice(null);
        } else if (dossier.getBackOffice() == null) {
            findAcceptedPrincipalDossier(dossier.getCommercant())
                .map(dossier_affiliation::getBackOffice)
                .ifPresent(dossier::setBackOffice);
        }
        dossier.setMotifRefus(null);
        dossier.setDateTraitementBackOffice(null);
        dossier.setSignedContractPath(null);
        dossier.setSignedContractFileName(null);
        dossier.setSignedContractUploadedAt(null);

        ServiceDocumentContratAffiliation.CompteRenduCommercialGenere compteRenduCommercial =
            serviceDocumentContratAffiliation.genererCompteRenduCommercial(dossier);
        dossier.setCommercialReportPath(compteRenduCommercial.cheminStocke());
        dossier.setCommercialReportFileName(compteRenduCommercial.nomFichier());
        dossier.setCommercialReportGeneratedAt(compteRenduCommercial.dateGeneration());

        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        if (isCommercialDirectDossier(dossier)) {
            dossier.setProspectStatus(ProspectStatus.EN_NEGOCIATION);
        }

        commercant commercant = dossier.getCommercant();
        if (commercant == null || commercant.getUtilisateur() == null) {
            throw new IllegalArgumentException("Le compte commerçant lie a ce dossier est introuvable.");
        }

        dossierAffiliationRepository.save(dossier);
        notifyBackOfficeDossierReadyForValidation(dossier);

        return new AffiliationActionResponse(
            newPdvRequest
                ? "La demande PDV a été complétée. Le compte-rendu a été généré et transmis au back office pour validation."
                : "Le dossier a été complète. Le compte-rendu commercial a été généré et le dossier transmis au back office pour validation."
        );
    }

    public AffiliationActionResponse createCommercialDraft(
        String authorizationHeader,
        CommercialAffiliationDraftRequest request
    ) {
        return createCommercialDraft(authorizationHeader, request, Map.of());
    }

    public AffiliationActionResponse createCommercialDraft(
        String authorizationHeader,
        CommercialAffiliationDraftRequest request,
        Map<String, MultipartFile> uploadedDocuments
    ) {
        commerciale authenticatedCommerciale = readAuthenticatedCommerciale(authorizationHeader);
        if (request == null) {
            throw new IllegalArgumentException("Les donnees du dossier sont obligatoires.");
        }

        TypeCommercant typeCommercant = mapMerchantType(request.getTypeCommercant());
        TypeAffiliation typeAffiliation = mapAffiliationType(request.getTypeAffiliation());

        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail(resolveDraftEmail(request.getEmail()));
        merchantUser.setPassword(null);
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(Boolean.FALSE);
        merchantUser.setDateActivation(null);
        merchantUser.setPasswordExpiresAt(null);
        merchantUser.setTokenVersion(0);
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        applyMerchantFields(commercant, request, typeCommercant);
        commercantRepository.save(commercant);
        saveOrUpdateSpecificMerchantProfile(request, commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(authenticatedCommerciale);
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setProspectStatus(ProspectStatus.NOUVEAU);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setTypeAffiliation(typeAffiliation);
        applyDossierBaseFields(dossier, request);
        applyNegotiableFieldsWithoutValidation(dossier, request);
        applyCommercialReportFieldsWithoutValidation(dossier, request);
        dossierAffiliationRepository.save(dossier);
        savePointVentes(request, commercant);
        saveCommercialDocuments(request, dossier, uploadedDocuments);

        return new AffiliationActionResponse(
            "La demande a été créée en brouillon par la commerciale. Elle peut être complétée plus tard.",
            dossier.getIdDossier()
        );
    }

    public AffiliationActionResponse saveCommercialDraft(
        String authorizationHeader,
        Long dossierId,
        CommercialAffiliationDraftRequest request
    ) {
        return saveCommercialDraft(authorizationHeader, dossierId, request, Map.of());
    }

    public AffiliationActionResponse saveCommercialDraft(
        String authorizationHeader,
        Long dossierId,
        CommercialAffiliationDraftRequest request,
        Map<String, MultipartFile> uploadedDocuments
    ) {
        commerciale authenticatedCommerciale = readAuthenticatedCommerciale(authorizationHeader);
        if (request == null) {
            throw new IllegalArgumentException("Les donnees du dossier sont obligatoires.");
        }

        dossier_affiliation dossier = readDossier(dossierId);
        validateDraftEligibility(dossier, authenticatedCommerciale);

        TypeCommercant typeCommercant = mapMerchantType(request.getTypeCommercant());
        TypeAffiliation typeAffiliation = mapAffiliationType(request.getTypeAffiliation());
        commercant commercant = dossier.getCommercant();
        if (commercant == null) {
            commercant = new commercant();
            dossier.setCommercant(commercant);
        }

        utilisateur merchantUser = commercant.getUtilisateur();
        if (merchantUser == null) {
            merchantUser = new utilisateur();
            merchantUser.setPassword(null);
            merchantUser.setRole(RoleUser.COMMERCANT);
            merchantUser.setActive(Boolean.FALSE);
            merchantUser.setTokenVersion(0);
            commercant.setUtilisateur(merchantUser);
        }
        updateDraftEmail(merchantUser, request.getEmail());
        utilisateurRepository.save(merchantUser);

        applyMerchantFields(commercant, request, typeCommercant);
        commercantRepository.save(commercant);
        saveOrUpdateSpecificMerchantProfile(request, commercant);

        dossier.setCommerciale(authenticatedCommerciale);
        if (dossier.getStatus() != StatusDossier.INCOMPLET) {
            dossier.setStatus(StatusDossier.BROUILLON);
        }
        if (dossier.getProspectStatus() == null) {
            dossier.setProspectStatus(ProspectStatus.NOUVEAU);
        }
        // Ne pas réécrire origineCreation ici : cette méthode met à jour un dossier
        // existant (brouillon commercial OU correction d'un dossier auto-affiliation/
        // nouveau PDV renvoyé par le BOA). L'origine est fixée une seule fois à la
        // création (createCommercialDraft / AffiliationRegistrationService /
        // MerchantWorkspaceManagementService) et ne doit plus changer ensuite, sinon un
        // dossier auto-affiliation corrigé se retrouve reclassé "prospection commerciale".
        dossier.setTypeAffiliation(typeAffiliation);
        applyDossierBaseFields(dossier, request);
        applyNegotiableFieldsWithoutValidation(dossier, request);
        applyCommercialReportFieldsWithoutValidation(dossier, request);
        dossierAffiliationRepository.save(dossier);
        savePointVentes(request, commercant);
        saveCommercialDocuments(request, dossier, uploadedDocuments);

        return new AffiliationActionResponse(
            "Le brouillon de la demande commerciale a été enregistré.",
            dossier.getIdDossier()
        );
    }

    @Transactional(readOnly = true)
    public CommercialInteractionResponse getCommercialInteractions(
        String authorizationHeader,
        Long dossierId
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );
        dossier_affiliation dossier = readDossier(dossierId);
        validateStaffCanAccessDossier(authenticatedUser, dossier);

        return new CommercialInteractionResponse(
            interactionCommercialeRepository
                .findByDossierAffiliation_IdDossierOrderByDateInteractionDescIdInteractionDesc(dossierId)
                .stream()
                .map(this::mapCommercialInteraction)
                .toList()
        );
    }

    public CommercialInteractionResponse addCommercialInteraction(
        String authorizationHeader,
        Long dossierId,
        CommercialInteractionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Les informations de l'interaction sont obligatoires.");
        }

        commerciale authenticatedCommerciale = readAuthenticatedCommerciale(authorizationHeader);
        dossier_affiliation dossier = readDossier(dossierId);
        if (!isCommercialDirectDossier(dossier)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Les interactions commerciales sont réservées aux demandes créées par commerciale."
            );
        }
        validateCommercialDirectOwnership(dossier, authenticatedCommerciale);
        validateCommercialInteractionEligibility(dossier);

        TypeInteraction typeInteraction = parseInteractionType(
            request.typeInteraction(),
            "Le type d'interaction est obligatoire."
        );
        TypeInteraction nextInteractionType = parseOptionalInteractionType(
            request.prochaineRelanceType()
        );
        LocalDate interactionDate = parseOptionalDate(
            request.dateInteraction(),
            LocalDate.now()
        );
        LocalDate nextReminderDate = parseOptionalDate(
            request.prochaineRelanceDate(),
            null
        );
        ProspectStatus nextProspectStatus = resolveNextProspectStatus(
            dossier,
            typeInteraction,
            request.prospectStatus(),
            nextReminderDate
        );
        String interactionResult = normalize(request.resultat());
        String interactionComment = normalize(request.commentaire());
        String interactionStatus = firstNotBlank(normalize(request.statut()), "FAIT");
        validateCommercialInteractionBusinessRules(
            typeInteraction,
            nextInteractionType,
            interactionDate,
            nextReminderDate,
            nextProspectStatus,
            interactionResult,
            interactionComment,
            interactionStatus
        );

        interaction_commerciale interaction = new interaction_commerciale();
        interaction.setDossierAffiliation(dossier);
        interaction.setCommerciale(authenticatedCommerciale);
        interaction.setTypeInteraction(typeInteraction);
        interaction.setDateInteraction(interactionDate);
        interaction.setResultat(interactionResult);
        interaction.setCommentaire(interactionComment);
        interaction.setStatut(interactionStatus);
        interaction.setProchaineRelanceDate(nextReminderDate);
        interaction.setProchaineRelanceType(nextInteractionType);
        interaction.setProspectStatus(nextProspectStatus.name());
        interactionCommercialeRepository.save(interaction);

        dossier.setProspectStatus(nextProspectStatus);
        dossier.setDerniereInteractionCommerciale(interactionDate);
        dossier.setDerniereInteractionType(typeInteraction);
        dossier.setProchaineRelanceCommerciale(nextReminderDate);
        dossier.setProchaineRelanceType(nextInteractionType);
        dossierAffiliationRepository.save(dossier);

        GoogleCalendarService.SyncResult calendarSync = googleCalendarService == null
            ? GoogleCalendarService.SyncResult.notAttempted(null)
            : googleCalendarService.createReminder(
                authenticatedCommerciale.getUtilisateur(),
                interaction,
                dossier,
                nextReminderDate
            );
        MicrosoftCalendarService.SyncResult microsoftCalendarSync = microsoftCalendarService == null
            ? MicrosoftCalendarService.SyncResult.notAttempted(null)
            : microsoftCalendarService.createReminder(
                authenticatedCommerciale.getUtilisateur(),
                interaction,
                dossier,
                nextReminderDate
            );
        CommercialInteractionResponse response = getCommercialInteractions(authorizationHeader, dossierId);
        return new CommercialInteractionResponse(
            response.interactions(),
            calendarSync.attempted() ? calendarSync.synced() : null,
            calendarSync.message(),
            calendarSync.eventUrl(),
            microsoftCalendarSync.attempted() ? microsoftCalendarSync.synced() : null,
            microsoftCalendarSync.message(),
            microsoftCalendarSync.eventUrl()
        );
    }

    public AffiliationActionResponse reviewMerchantDossier(
        String authorizationHeader,
        Long dossierId,
        AffiliationReviewRequest request
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.BACK_OFFICE
        );
        back_office authenticatedBackOffice = backOfficeRepository
            .findByUtilisateur_Id(authenticatedUser.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte back office introuvable."
                )
            );

        dossier_affiliation dossier = readDossier(dossierId);
        validateReviewEligibility(dossier);
        validateExtensionBackOfficeOwnership(dossier, authenticatedBackOffice);

        String decision = normalize(request == null ? null : request.decision());
        if (!StringUtils.hasText(decision)) {
            throw new IllegalArgumentException("La decision du back office est obligatoire.");
        }
        String motifRefus = normalize(request == null ? null : request.motifRefus());

        return switch (decision.toUpperCase(Locale.ROOT)) {
            case "ACCEPTE", "VALIDE", "APPROVE" -> {
                if (StringUtils.hasText(motifRefus)) {
                    throw new IllegalArgumentException(
                        "La validation est impossible tant qu'un motif de correction est renseigne."
                    );
                }
                yield approveDossierForContract(authenticatedBackOffice, dossier);
            }
            case "CORRECTION", "DEMANDER_CORRECTION", "REFUSE", "REJECT" -> sendDossierBackToCommercial(
                authenticatedBackOffice,
                dossier,
                motifRefus
            );
            default -> throw new IllegalArgumentException("Decision back office invalide.");
        };
    }

    public AffiliationActionResponse abandonCorrectionDossier(
        String authorizationHeader,
        Long dossierId,
        AffiliationAbandonRequest request
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.COMMERCIAL
        );
        commerciale authenticatedCommerciale = commercialeRepository
            .findByUtilisateur_Id(authenticatedUser.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte commercial introuvable."
                )
            );

        dossier_affiliation dossier = readDossier(dossierId);
        validateStaffCanAccessDossier(authenticatedUser, dossier);
        if (dossier.getStatus() != StatusDossier.INCOMPLET) {
            throw new IllegalArgumentException(
                "Seul un dossier renvoyé pour correction peut être abandonné."
            );
        }

        String motif = normalize(request == null ? null : request.motif());
        requireText(motif, "Le motif d'abandon est obligatoire.");

        dossier.setStatus(StatusDossier.ABANDONNE);
        dossier.setProspectStatus(ProspectStatus.ABANDONNE);
        dossier.setMotifRefus(motif);
        dossier.setDateTraitementBackOffice(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        notifyDossierAbandoned(dossier, motif, authenticatedCommerciale);

        return new AffiliationActionResponse(
            "Le dossier a été abandonné. Aucun compte commerçant, contrat ou conversion ne sera créé."
        );
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(
        String authorizationHeader,
        Long dossierId,
        Long documentId
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );
        validateStaffCanAccessDossier(authenticatedUser, readDossier(dossierId));

        documents document = documentsRepository
            .findByIdDocumentAndDossierAffiliation_IdDossier(documentId, dossierId)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Le document demande est introuvable."
                )
            );

        String storedPath = document.getCheminStockage();
        if (!StringUtils.hasText(storedPath)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Le document n'est pas disponible au telechargement."
            );
        }

        Path filePath = resolveUploadedDocumentPath(storedPath);
        if (!isExistingFile(filePath)) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Le document n'est pas disponible au telechargement."
            );
        }

        try {
            String contentType = Files.probeContentType(filePath);
            return new DocumentDownload(
                resolveDocumentFileName(storedPath),
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                Files.readAllBytes(filePath)
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de telecharger le document."
            );
        }
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadContratGenere(
        String authorizationHeader,
        Long dossierId
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );
        dossier_affiliation dossier = readDossier(dossierId);
        validateStaffCanAccessDossier(authenticatedUser, dossier);
        return toDocumentDownload(
            serviceDocumentContratAffiliation.telechargerFichier(dossier.getGeneratedContractPath())
        );
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadSignedContract(
        String authorizationHeader,
        Long dossierId
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );
        dossier_affiliation dossier = readDossier(dossierId);
        validateStaffCanAccessDossier(authenticatedUser, dossier);
        return toDocumentDownload(
            serviceDocumentContratAffiliation.telechargerFichier(dossier.getSignedContractPath())
        );
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadCommercialReport(
        String authorizationHeader,
        Long dossierId
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );
        dossier_affiliation dossier = readDossier(dossierId);
        validateStaffCanAccessDossier(authenticatedUser, dossier);
        return toDocumentDownload(
            serviceDocumentContratAffiliation.telechargerFichier(dossier.getCommercialReportPath())
        );
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadFullDossier(
        String authorizationHeader,
        Long dossierId
    ) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.SUPERVISEUR,
            RoleUser.COMMERCIAL,
            RoleUser.BACK_OFFICE
        );
        dossier_affiliation dossier = readDossier(dossierId);
        validateStaffCanAccessDossier(authenticatedUser, dossier);

        if (isNewPdvProductRequest(dossier)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Le dossier complet fusionne n'est disponible que pour les demandes d'auto-affiliation et de prospection."
            );
        }

        List<documents> documentsDeposes =
            documentsRepository.findAllByDossierAffiliation_IdDossierOrderByDateUploadDescIdDocumentDesc(dossierId);

        byte[] contratPdf = readOptionalGeneratedFile(dossier.getGeneratedContractPath());
        byte[] compteRenduPdf = readOptionalGeneratedFile(dossier.getCommercialReportPath());

        List<ServiceDocumentContratAffiliation.DocumentAFusionner> documentsAFusionner = documentsDeposes
            .stream()
            .map(this::toDocumentAFusionner)
            .filter(Objects::nonNull)
            .toList();

        byte[] mergedPdf = serviceDocumentContratAffiliation.genererDossierComplet(
            dossier,
            documentsDeposes,
            contratPdf,
            compteRenduPdf,
            documentsAFusionner
        );

        return new DocumentDownload(
            "dossier-" + dossierId + "-complet.pdf",
            "application/pdf",
            mergedPdf
        );
    }

    private byte[] readOptionalGeneratedFile(String storedPath) {
        if (!StringUtils.hasText(storedPath) || !serviceDocumentContratAffiliation.fichierDisponible(storedPath)) {
            return null;
        }
        return serviceDocumentContratAffiliation.telechargerFichier(storedPath).contenu();
    }

    private ServiceDocumentContratAffiliation.DocumentAFusionner toDocumentAFusionner(documents document) {
        String storedPath = document.getCheminStockage();
        if (!StringUtils.hasText(storedPath)) {
            return null;
        }

        Path filePath = resolveUploadedDocumentPath(storedPath);
        if (!isExistingFile(filePath)) {
            return null;
        }

        try {
            String contentType = Files.probeContentType(filePath);
            return new ServiceDocumentContratAffiliation.DocumentAFusionner(
                resolveDocumentFileName(storedPath),
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                Files.readAllBytes(filePath)
            );
        } catch (IOException exception) {
            return null;
        }
    }

    private StaffAffiliationOverviewResponse.AffiliationRequestItem mapRequestItem(
        dossier_affiliation dossier
    ) {
        commercant commercant = dossier.getCommercant();
        utilisateur utilisateur = commercant == null ? null : commercant.getUtilisateur();
        MerchantProfileSnapshot merchantProfile = resolveMerchantProfile(commercant);
        boolean newPdvRequest = isNewPdvProductRequest(dossier);
        dossier_affiliation principalDossier = newPdvRequest
            ? findAcceptedPrincipalDossier(commercant).orElse(null)
            : null;
        commerciale commerciale = firstNonNull(
            dossier.getCommercialeAssignee(),
            firstNonNull(
                dossier.getCommerciale(),
                principalDossier == null ? null : principalDossier.getCommerciale()
            )
        );
        back_office backOffice = firstNonNull(
            dossier.getBackOffice(),
            principalDossier == null ? null : principalDossier.getBackOffice()
        );
        pdv requestedPdv = dossier.getRequestedPdv();
        Integer nombreDemandesExtention = countExtensionRequests(commercant);
        List<StaffAffiliationOverviewResponse.AffiliationDocumentItem> documents =
            documentsRepository
                .findAllByDossierAffiliation_IdDossierOrderByDateUploadDescIdDocumentDesc(
                    dossier.getIdDossier()
                )
                .stream()
                .map(
                    document ->
                        new StaffAffiliationOverviewResponse.AffiliationDocumentItem(
                            document.getIdDocument(),
                            document.getTypeDocument() == null ? "" : document.getTypeDocument().name(),
                            resolveDocumentFileName(document.getCheminStockage()),
                            document.getDateUpload(),
                            document.getStatutDocument() == null
                                ? ""
                                : document.getStatutDocument().name(),
                            isDownloadableDocument(document)
                        )
                )
                .toList();

        return new StaffAffiliationOverviewResponse.AffiliationRequestItem(
            dossier.getIdDossier(),
            commercant == null ? null : commercant.getIdCommercant(),
            utilisateur == null ? null : utilisateur.getId(),
            commercant == null
                ? ""
                : safe(firstNotBlank(commercant.getNomCommercial(), commercant.getRaisonSociale())),
            utilisateur == null ? "" : safe(utilisateur.getEmail()),
            commercant == null ? "" : safe(firstNotBlank(commercant.getTelephone(), commercant.getTelephoneSecondaire())),
            commercant == null ? "" : safe(commercant.getTelephoneSecondaire()),
            commercant == null ? "" : safe(commercant.getAdresse()),
            commercant == null ? "" : safe(commercant.getVille()),
            commercant == null ? "" : safe(commercant.getRegion()),
            commercant == null ? "" : safe(commercant.getActivite()),
            commercant == null ? "" : safe(commercant.getSecteur()),
            commercant == null ? "" : safe(commercant.getMcc()),
            commercant == null ? "" : safe(commercant.getChainePointVente()),
            commercant == null ? null : commercant.getNombrePointsVente(),
            commercant == null || commercant.getType() == null ? "" : commercant.getType().name(),
            dossier.getTypeAffiliation() == null ? "" : dossier.getTypeAffiliation().name(),
            safe(firstNotBlank(dossier.getOrigineCreation(), "AUTO_AFFILIATION")),
            dossier.getProspectStatus() == null ? "" : dossier.getProspectStatus().name(),
            dossier.getDerniereInteractionCommerciale(),
            dossier.getProchaineRelanceCommerciale(),
            dossier.getDerniereInteractionType() == null ? "" : dossier.getDerniereInteractionType().name(),
            dossier.getProchaineRelanceType() == null ? "" : dossier.getProchaineRelanceType().name(),
            safe(dossier.getRib()),
            safe(merchantProfile.nom()),
            safe(merchantProfile.prenom()),
            safe(merchantProfile.cin()),
            safe(
                firstNotBlank(
                    merchantProfile.raisonSociale(),
                    commercant == null ? "" : commercant.getRaisonSociale()
                )
            ),
            safe(
                firstNotBlank(
                    merchantProfile.rc(),
                    commercant == null ? "" : commercant.getRegistreCommerce()
                )
            ),
            safe(
                firstNotBlank(
                    merchantProfile.ice(),
                    commercant == null ? "" : commercant.getIdentifiantFiscal()
                )
            ),
            safe(merchantProfile.formeJuridique()),
            safe(merchantProfile.representantLegal()),
            safe(merchantProfile.numeroAutoEntrepreneur()),
            safe(merchantProfile.nomEntite()),
            safe(merchantProfile.objet()),
            commercant == null ? "" : safe(commercant.getPatente()),
            commercant == null ? "" : safe(commercant.getFonction()),
            commercant == null ? "" : safe(commercant.getBeneficiairesEffectifs()),
            commercant == null ? "" : safe(commercant.getDateNaissance()),
            commercant == null ? "" : safe(commercant.getNationalite()),
            dossier.getStatus() == null ? "" : dossier.getStatus().name(),
            dossier.getDateCreation(),
            dossier.getDateSoumission(),
            utilisateur != null && Boolean.TRUE.equals(utilisateur.getActive()),
            utilisateur != null
                && !Boolean.TRUE.equals(utilisateur.getActive())
                && utilisateur.getPasswordExpiresAt() != null,
            resolveCommercialDisplayName(commerciale),
            commerciale == null ? null : commerciale.getIdCommercial(),
            resolveBackOfficeDisplayName(backOffice),
            backOffice == null ? null : backOffice.getIdBackOffice(),
            backOffice == null || backOffice.getUtilisateur() == null
                ? null
                : backOffice.getUtilisateur().getId(),
            safe(dossier.getMotifRefus()),
            dossier.getDateTraitementBackOffice(),
            serviceDocumentContratAffiliation.fichierDisponible(dossier.getGeneratedContractPath()),
            safe(dossier.getGeneratedContractFileName()),
            dossier.getGeneratedContractAt(),
            serviceDocumentContratAffiliation.fichierDisponible(dossier.getCommercialReportPath()),
            safe(dossier.getCommercialReportFileName()),
            dossier.getCommercialReportGeneratedAt(),
            serviceDocumentContratAffiliation.fichierDisponible(dossier.getSignedContractPath()),
            safe(dossier.getSignedContractFileName()),
            dossier.getSignedContractUploadedAt(),
            documents,
            safe(dossier.getModeMiseADispositionTpe()),
            dossier.getNombreTpe(),
            safe(dossier.getEquipementTpe()),
            safe(dossier.getConnectiviteTpe()),
            safe(dossier.getModeServiceEcommerce()),
            safe(dossier.getSiteMarchandUrl()),
            safe(dossier.getApplicationMobile()),
            safe(dossier.getModeleQrSoftpos()),
            safe(dossier.getCommissionLocaleTpe()),
            safe(dossier.getCommissionEtrangereTpe()),
            safe(dossier.getDepotTpe()),
            safe(dossier.getPrixAchatTpe()),
            safe(dossier.getPrixLicenceTpe()),
            safe(dossier.getAbonnementPackage()),
            safe(dossier.getCommissionLocaleEcommerce()),
            safe(dossier.getCommissionEtrangereEcommerce()),
            safe(dossier.getFraisMiseEnServiceEcommerce()),
            safe(dossier.getCommissionLocaleQrSoftpos()),
            safe(dossier.getCommissionEtrangereQrSoftpos()),
            safe(dossier.getFraisServiceQrSoftpos()),
            safe(dossier.getConditionsQrSoftpos()),
            Boolean.TRUE.equals(dossier.getServiceCreditVoucher()),
            Boolean.TRUE.equals(dossier.getServiceAnnulation()),
            Boolean.TRUE.equals(dossier.getServiceDcc()),
            Boolean.TRUE.equals(dossier.getServicePreAutorisationCartePresente()),
            Boolean.TRUE.equals(dossier.getServicePreAutorisationCartePresenteConfirmationManuelle()),
            Boolean.TRUE.equals(dossier.getServicePreAutorisationManuelleConfirmationCartePresente()),
            Boolean.TRUE.equals(dossier.getServiceTransactionManuelle()),
            Boolean.TRUE.equals(dossier.getServiceTransactionManuelleSansCvv()),
            safe(dossier.getCompteRenduQualification()),
            safe(dossier.getCompteRenduAcquereur()),
            safe(dossier.getCompteRenduOrigineProspect()),
            safe(dossier.getCompteRenduOrigineProspectDetail()),
            safe(dossier.getCompteRenduContactNomPrenom()),
            safe(dossier.getCompteRenduContactFonction()),
            safe(dossier.getCompteRenduPointVenteAcronyme()),
            safe(dossier.getCompteRenduActionnaires()),
            safe(dossier.getCompteRenduCommercant()),
            safe(dossier.getCompteRenduChaine()),
            safe(dossier.getCompteRenduRelationsLc()),
            safe(dossier.getCompteRenduDateOuverture()),
            safe(dossier.getCompteRenduNombreEmployes()),
            safe(dossier.getCompteRenduActivite()),
            safe(dossier.getCompteRenduMcc()),
            safe(dossier.getCompteRenduStandingMagasin()),
            safe(dossier.getCompteRenduNatureMarchandises()),
            safe(dossier.getCompteRenduSuperficieLocal()),
            safe(dossier.getCompteRenduStatutLocal()),
            safe(dossier.getCompteRenduChiffreAffairesAnnuel()),
            safe(dossier.getCompteRenduPartPaiementCarte()),
            safe(dossier.getCompteRenduPartCarteLocale()),
            safe(dossier.getCompteRenduProfilCommercant()),
            safe(dossier.getCompteRenduAppreciationVisite()),
            safe(dossier.getCompteRenduCommentaire()),
            safe(dossier.getCompteRenduFaitA()),
            safe(dossier.getCompteRenduDateVisite()),
            principalDossier == null ? null : principalDossier.getIdDossier(),
            nombreDemandesExtention,
            requestedPdv == null ? "" : safe(requestedPdv.getNomPDV()),
            requestedPdv == null ? "" : safe(requestedPdv.getAdresse()),
            requestedPdv == null ? "" : safe(requestedPdv.getVille()),
            requestedPdv == null ? "" : safe(requestedPdv.getCodePostal()),
            requestedPdv == null ? "" : safe(requestedPdv.getTelephone()),
            requestedPdv == null ? "" : safe(requestedPdv.getEmail()),
            requestedPdv == null ? "" : safe(requestedPdv.getStatut()),
            isTpeAlreadyFullyAssigned(dossier, commercant),
            dossier.getNombreCorrections() == null ? 0 : dossier.getNombreCorrections(),
            safe(dossier.getDernierMotifCorrection())
        );
    }

    private boolean isTpeAlreadyFullyAssigned(dossier_affiliation dossier, commercant commercant) {
        if (commercant == null || commercant.getIdCommercant() == null
            || dossier.getTypeAffiliation() == TypeAffiliation.E_COMMERCE) {
            return false;
        }
        Integer requestedCount = dossier.getNombreTpe();
        int required = requestedCount != null && requestedCount > 0 ? requestedCount : 1;
        // La table locale "tpe" ne couvre que le provisionnement auto pour les
        // demandes NOUVEAU_PDV. Le flux BOA principal (assignTpeToCommercant)
        // affecte le TPE uniquement côté Oracle — sans ce comptage, ce champ
        // restait faussement à false pour ce flux (voir MerchantAccessService::
        // hasTpeAssignedInOracle, même correctif).
        long assignedCount = tpeRepository.countByPdv_Commercant_IdCommercantAndStatut(
            commercant.getIdCommercant(),
            "AFFECTE_COMMERCANT"
        ) + countTpeAssignedInOracle(commercant.getIdCommercant());
        return assignedCount >= required;
    }

    private long countTpeAssignedInOracle(Long commercantId) {
        try {
            String idCommercant = commercantId.toString();
            return switchMonetiqueClient.stockComplet().stream()
                .filter(tpe -> idCommercant.equals(tpe.idCommercant()))
                .count();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "[StaffAffiliationManagementService] switch-monetique-service injoignable, "
                    + "impossible de vérifier l'affectation TPE Oracle pour le commerçant {}.",
                commercantId,
                exception
            );
            return 0L;
        }
    }

    private MerchantProfileSnapshot resolveMerchantProfile(commercant commercant) {
        if (commercant == null || commercant.getIdCommercant() == null || commercant.getType() == null) {
            return MerchantProfileSnapshot.empty();
        }

        Long commercantId = commercant.getIdCommercant();

        return switch (commercant.getType()) {
            case PERSONNE_PHYSIQUE ->
                ppRepository
                    .findByCommercant_IdCommercant(commercantId)
                    .map(
                        pp ->
                            new MerchantProfileSnapshot(
                                pp.getNom(),
                                pp.getPrenom(),
                                pp.getCin(),
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                ""
                            )
                    )
                    .orElseGet(MerchantProfileSnapshot::empty);
            case PERSONNE_MORALE ->
                pmRepository
                    .findByCommercant_IdCommercant(commercantId)
                    .map(
                        pm ->
                            new MerchantProfileSnapshot(
                                "",
                                "",
                                "",
                                pm.getRaisonSociale(),
                                pm.getRegistreCommerce(),
                                pm.getIce(),
                                pm.getFormeJuridique(),
                                pm.getRepresentantLegal(),
                                "",
                                "",
                                ""
                            )
                    )
                    .orElseGet(MerchantProfileSnapshot::empty);
            case AUTO_ENTREPRENEUR ->
                aeRepository
                    .findByCommercant_IdCommercant(commercantId)
                    .map(
                        ae ->
                            new MerchantProfileSnapshot(
                                ae.getNom(),
                                ae.getPrenom(),
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                ae.getNumeroAutoEntrepreneur(),
                                "",
                                ""
                            )
                    )
                    .orElseGet(MerchantProfileSnapshot::empty);
            case ASSOCIATION_FONDATION ->
                associationRepository
                    .findByCommercant_IdCommercant(commercantId)
                    .map(
                        association ->
                            new MerchantProfileSnapshot(
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                association.getRepresentantLegal(),
                                "",
                                association.getNomEntite(),
                                association.getObjet()
                            )
                    )
                    .orElseGet(MerchantProfileSnapshot::empty);
        };
    }

    private AffiliationActionResponse approveDossierForContract(
        back_office authenticatedBackOffice,
        dossier_affiliation dossier
    ) {
        boolean newPdvRequest = isNewPdvProductRequest(dossier);

        commercant commercant = dossier.getCommercant();
        if (commercant == null || commercant.getUtilisateur() == null) {
            throw new IllegalArgumentException("Le compte commerçant lie a ce dossier est introuvable.");
        }

        utilisateur utilisateur = commercant.getUtilisateur();
        if (utilisateur.getDateDesactivation() != null) {
            throw new IllegalArgumentException(
                "Le compte commerçant a été desactive. Reactivez-le avant de relancer l'activation."
            );
        }

        ServiceDocumentContratAffiliation.ContratGenere contratGenere =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setGeneratedContractPath(contratGenere.cheminStocke());
        dossier.setGeneratedContractFileName(contratGenere.nomFichier());
        dossier.setGeneratedContractAt(contratGenere.dateGeneration());

        // ── Double écriture vers la table normalisée contrats ─────────────────
        // Dossier combine (encaissement + e-commerce) : le PDF fusionne les deux
        // contrats, mais on garde une ligne "contrat" par type pour le bookkeeping,
        // toutes deux pointant vers ce meme fichier fusionne.
        if (dossier.getTypeAffiliation() == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE) {
            enregistrerLigneContrat(dossier, contratGenere, resolveEncaissementProductType(dossier));
            enregistrerLigneContrat(dossier, contratGenere, TypeContrat.E_COMMERCE);
        } else {
            enregistrerLigneContrat(dossier, contratGenere, resolveTypeContrat(dossier));
        }
        // ─────────────────────────────────────────────────────────────────────

        dossier.setBackOffice(authenticatedBackOffice);
        dossier.setMotifRefus(null);
        dossier.setDateTraitementBackOffice(LocalDate.now());
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);

        boolean activationEmailSent = true;
        if (!newPdvRequest) {
            String temporaryPassword = generateTemporaryPassword();
            utilisateur.setPassword(null);
            utilisateur.setPasswordExpiresAt(
                LocalDateTime.now().plusMinutes(activationExpirationMinutes)
            );
            utilisateur.setActive(Boolean.FALSE);
            utilisateur.setDateActivation(null);
            utilisateur.setTokenVersion(resolveTokenVersion(utilisateur) + 1);
            clearPendingAuthentication(utilisateur);
            if (!keycloakAdminService.provisionUser(utilisateur, temporaryPassword)) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le compte Keycloak n'a pas pu être préparé. Aucun e-mail d'activation n'a été envoyé."
                );
            }

            ActivationMailService.MailDispatchResult dispatchResult =
                activationMailService.sendActivationEmail(utilisateur, commercant, temporaryPassword);

            activationEmailSent = dispatchResult.sent();
            if (!activationEmailSent) {
                LOGGER.warn(
                    "E-mail d'activation non envoyé pour le dossier #{} : {}",
                    dossier.getIdDossier(),
                    dispatchResult.message()
                );
            }

            utilisateurRepository.save(utilisateur);
        }
        dossierAffiliationRepository.save(dossier);

        boolean newPdvContractEmailSent = true;
        if (newPdvRequest) {
            newPdvContractEmailSent = affiliationStatusMailService.sendStatusUpdateEmail(
                utilisateur.getEmail(),
                resolveMerchantDisplayName(commercant, utilisateur),
                "Contrat nouveau point de vente disponible #" + dossier.getIdDossier(),
                buildNewPdvContractAvailableEmailBody(
                    dossier,
                    resolveMerchantDisplayName(commercant, utilisateur)
                )
            );
        }
        createNotification(
            utilisateur,
            dossier.getIdDossier(),
            newPdvRequest
                ? "Votre contrat de nouveau point de vente est disponible. Consultez-le, signez-le puis televersez-le depuis votre espace commerçant."
                : "Votre contrat d'affiliation est disponible. Connectez-vous pour le consulter, l'imprimer, le signer puis le televerser.",
            TypeNotification.CONTRAT_GENERE
        );

        return new AffiliationActionResponse(
            newPdvRequest
                ? newPdvContractEmailSent
                    ? "Le dossier a été validé par le back office. Le contrat de nouveau point de vente et l'e-mail au commerçant ont été générés."
                    : "Le dossier a été validé par le back office. Le contrat a été généré, mais l'e-mail au commerçant n'a pas pu être envoyé."
                : activationEmailSent
                    ? "Le dossier a été validé par le back office. Le contrat et l'e-mail d'activation ont été générés."
                    : "Le dossier a été validé par le back office. Le contrat a été généré. L'e-mail d'activation n'a pas pu etre envoyé (SMTP non configuré)."
        );
    }

    private AffiliationActionResponse sendDossierBackToCommercial(
        back_office authenticatedBackOffice,
        dossier_affiliation dossier,
        String motifRefus
    ) {
        requireText(motifRefus, "Le motif de renvoi est obligatoire.");
        validateStructuredCorrectionMotif(motifRefus);

        dossier.setBackOffice(authenticatedBackOffice);
        dossier.setMotifRefus(normalize(motifRefus));
        dossier.setDernierMotifCorrection(normalize(motifRefus));
        dossier.setNombreCorrections(
            (dossier.getNombreCorrections() == null ? 0 : dossier.getNombreCorrections()) + 1
        );
        dossier.setDateTraitementBackOffice(LocalDate.now());
        dossier.setStatus(StatusDossier.INCOMPLET);
        dossierAffiliationRepository.save(dossier);

        notifyDossierReturnedForCorrection(dossier, dossier.getMotifRefus());

        return new AffiliationActionResponse(
            "Le dossier a été marqué incomplet et renvoyé au commercial pour correction. Le commercial a été informe du motif."
        );
    }

    private void validateStructuredCorrectionMotif(String motifRefus) {
        String normalizedMotif = normalize(motifRefus);
        boolean hasCategory = normalizedMotif.contains("Types de problème:")
            && !normalizedMotif.contains("Types de problème: -");
        boolean hasDetail = normalizedMotif.contains("Motif:")
            && !normalizedMotif.matches("(?s).*Motif:\\s*$");
        if (!hasCategory || !hasDetail) {
            throw new IllegalArgumentException(
                "La demande de correction doit contenir un type de probleme et un motif detaille."
            );
        }
    }

    /**
     * Déclenché automatiquement lorsque le commerçant dépose un contrat signé valide
     * (bon template + zone de signature remplie) : plus de revue humaine après signature.
     */
    public void finalizeAutomaticAcceptance(dossier_affiliation dossier) {
        commercant commercant = dossier.getCommercant();
        if (commercant == null || commercant.getUtilisateur() == null) {
            throw new IllegalArgumentException("Le compte commerçant lie a ce dossier est introuvable.");
        }

        utilisateur utilisateur = commercant.getUtilisateur();
        if (utilisateur.getDateDesactivation() != null) {
            throw new IllegalArgumentException(
                "Le compte commerçant a été desactive. Reactivez-le avant de relancer l'activation."
            );
        }

        dossier.setMotifRefus(null);
        dossier.setDateTraitementBackOffice(LocalDate.now());
        dossier.setStatus(StatusDossier.ACCEPTE);
        // CONVERTI n'est plus posé ici : à ce stade, le contrat est signé et
        // validé mais aucun TPE n'a encore été affecté par le BOA. Le statut
        // CONVERTI (validé + contrat signé + TPE déjà affecté) est désormais
        // posé dans SupervisorManagementService.assignTpeToCommercant, une
        // fois l'affectation TPE réellement effectuée.
        if (isNewPdvProductRequest(dossier) && dossier.getRequestedPdv() != null) {
            dossier.getRequestedPdv().setStatut("ACTIF");
            pdv acceptedPdv = pdvRepository.save(dossier.getRequestedPdv());
            // Cas NOUVEAU_PDV : les terminaux sont auto-provisionnés ici même
            // (provisionRequestedTerminals), pas d'affectation manuelle BOA à
            // attendre — pas de notification "TPE à affecter" pour ce flux.
            provisionRequestedTerminals(dossier, acceptedPdv);
        } else {
            pdvRepository.updateStatutByCommercantId(commercant.getIdCommercant(), "ACTIF");
            if (dossier.getTypeAffiliation() != null && dossier.getTypeAffiliation() != TypeAffiliation.E_COMMERCE) {
                notifyBackOfficeTpeAssignmentNeeded(dossier);
            }
        }

        dossierAffiliationRepository.save(dossier);
        publishReviewOutcome(dossier, true, null);
    }

    /**
     * Alerte tous les BOA dès que le contrat est signé/déposé et que le
     * dossier nécessite une affectation TPE manuelle (tout sauf E_COMMERCE
     * pur — voir MerchantAccessService::workspaceUnlocked pour la même
     * distinction côté déblocage de l'espace commerçant).
     */
    private void notifyBackOfficeTpeAssignmentNeeded(dossier_affiliation dossier) {
        commercant commercant = dossier.getCommercant();
        String merchantName = resolveMerchantDisplayName(
            commercant,
            commercant == null ? null : commercant.getUtilisateur()
        );
        String message = "Le contrat du dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été signé — une référence TPE doit être affectée pour débloquer son espace.";

        for (back_office backOffice : backOfficeRepository.findAllByOrderByNomAscPrenomAscIdBackOfficeAsc()) {
            utilisateur backOfficeUser = backOffice.getUtilisateur();
            if (backOfficeUser == null) {
                continue;
            }
            createNotification(
                backOfficeUser,
                dossier.getIdDossier(),
                message,
                TypeNotification.DOSSIER_TPE_A_AFFECTER
            );
            affiliationStatusMailService.sendStatusUpdateEmail(
                backOfficeUser.getEmail(),
                firstNotBlank(
                    (safe(backOffice.getPrenom()) + " " + safe(backOffice.getNom())).trim(),
                    backOfficeUser.getEmail()
                ),
                "TPE à affecter — dossier #" + dossier.getIdDossier(),
                """
                Bonjour,

                Le contrat du dossier #%s de %s vient d'être signé et déposé.
                Une référence TPE doit maintenant être affectée pour débloquer
                l'espace commerçant.

                Cordialement,
                L'équipe Lana Cash
                """.formatted(dossier.getIdDossier(), merchantName)
            );
        }
    }

    private void notifyBackOfficeDossierReadyForValidation(dossier_affiliation dossier) {
        commercant commercant = dossier.getCommercant();
        String merchantName = resolveMerchantDisplayName(
            commercant,
            commercant == null ? null : commercant.getUtilisateur()
        );
        String message = "Le dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été complète par la commerciale et attend votre validation avant envoi du contrat.";

        List<back_office> recipients = isNewPdvProductRequest(dossier) && dossier.getBackOffice() != null
            ? List.of(dossier.getBackOffice())
            : backOfficeRepository.findAllByOrderByNomAscPrenomAscIdBackOfficeAsc();

        for (back_office backOffice : recipients) {
            utilisateur backOfficeUser = backOffice.getUtilisateur();
            if (backOfficeUser == null) {
                continue;
            }
            createNotification(
                backOfficeUser,
                dossier.getIdDossier(),
                message,
                TypeNotification.DOSSIER_A_VALIDER_BOA
            );
            affiliationStatusMailService.sendStatusUpdateEmail(
                backOfficeUser.getEmail(),
                firstNotBlank(
                    (safe(backOffice.getPrenom()) + " " + safe(backOffice.getNom())).trim(),
                    backOfficeUser.getEmail()
                ),
                "Dossier à valider avant envoi du contrat #" + dossier.getIdDossier(),
                """
                Bonjour,

                Le dossier #%s de %s a été complète par la commerciale et attend votre validation
                avant que le contrat ne soit généré et envoyé au commerçant pour signature.

                Cordialement,
                L'équipe Lana Cash
                """.formatted(dossier.getIdDossier(), merchantName)
            );
        }
    }

    private void notifyDossierReturnedForCorrection(dossier_affiliation dossier, String motifRefus) {
        notifyCommercialDossierReturned(dossier, motifRefus);
        notifySupervisorsDossierReturned(dossier, motifRefus);
    }

    private void notifyMerchantDossierReturned(dossier_affiliation dossier, String motifRefus) {
        commercant commercant = dossier.getCommercant();
        utilisateur merchantUser = commercant == null ? null : commercant.getUtilisateur();
        if (merchantUser == null) {
            return;
        }

        String merchantName = resolveMerchantDisplayName(commercant, merchantUser);
        String message = "Votre dossier #"
            + dossier.getIdDossier()
            + " nécessite un complément avant validation. Motif: "
            + safe(motifRefus);

        createNotification(
            merchantUser,
            dossier.getIdDossier(),
            message,
            TypeNotification.DOCUMENT_MANQUANT
        );
        affiliationStatusMailService.sendStatusUpdateEmail(
            merchantUser.getEmail(),
            merchantName,
            "Complément demandé pour votre dossier #" + dossier.getIdDossier(),
            """
            Bonjour %s,

            Le back office a demandé un complément sur votre dossier #%s avant validation.
            Motif indiqué : %s

            Votre commercial prendra contact avec vous pour corriger le dossier puis le soumettre à nouveau.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(merchantName, dossier.getIdDossier(), safe(motifRefus))
        );
    }

    private void notifyCommercialDossierReturned(dossier_affiliation dossier, String motifRefus) {
        commerciale commerciale = dossier.getCommerciale();
        utilisateur commercialUser = commerciale == null ? null : commerciale.getUtilisateur();
        if (commercialUser == null) {
            return;
        }

        commercant commercant = dossier.getCommercant();
        String merchantName = resolveMerchantDisplayName(
            commercant,
            commercant == null ? null : commercant.getUtilisateur()
        );
        String commercialDisplayName = resolveCommercialDisplayName(commerciale);
        String message = "Le dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été renvoyé par le back office pour correction. Motif: "
            + safe(motifRefus);

        createNotification(
            commercialUser,
            dossier.getIdDossier(),
            message,
            TypeNotification.DOSSIER_RENVOYE_COMMERCIAL
        );
        affiliationStatusMailService.sendStatusUpdateEmail(
            commercialUser.getEmail(),
            commercialDisplayName,
            "Dossier renvoyé pour correction #" + dossier.getIdDossier(),
            """
            Bonjour %s,

            Le back office a renvoyé le dossier #%s de %s pour correction avant l'envoi du contrat.
            Motif indiqué : %s

            Merci de corriger le dossier puis de le soumettre a nouveau.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(commercialDisplayName, dossier.getIdDossier(), merchantName, safe(motifRefus))
        );
    }

    private void notifySupervisorsDossierReturned(dossier_affiliation dossier, String motifRefus) {
        commercant commercant = dossier.getCommercant();
        String merchantName = resolveMerchantDisplayName(
            commercant,
            commercant == null ? null : commercant.getUtilisateur()
        );
        String message = "Le dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été renvoyé au commercial pour correction. Motif: "
            + safe(motifRefus);

        for (utilisateur supervisor : utilisateurRepository.findAllByRole(RoleUser.SUPERVISEUR)) {
            createNotification(
                supervisor,
                dossier.getIdDossier(),
                message,
                TypeNotification.DOSSIER_RENVOYE_COMMERCIAL
            );
            affiliationStatusMailService.sendStatusUpdateEmail(
                supervisor.getEmail(),
                firstNotBlank(supervisor.getEmail(), "Superviseur"),
                "Correction demandée sur le dossier #" + dossier.getIdDossier(),
                """
                Bonjour,

                Le back office a demandé une correction sur le dossier #%s de %s.
                Motif indiqué : %s

                Le dossier est revenu dans l'espace commercial pour correction.

                Cordialement,
                L'équipe Lana Cash
                """.formatted(dossier.getIdDossier(), merchantName, safe(motifRefus))
            );
        }
    }

    private void notifyDossierAbandoned(
        dossier_affiliation dossier,
        String motif,
        commerciale authenticatedCommerciale
    ) {
        commercant commercant = dossier.getCommercant();
        utilisateur merchantUser = commercant == null ? null : commercant.getUtilisateur();
        String merchantName = resolveMerchantDisplayName(commercant, merchantUser);

        if (merchantUser != null) {
            affiliationStatusMailService.sendStatusUpdateEmail(
                merchantUser.getEmail(),
                merchantName,
                "Dossier d'affiliation abandonné #" + dossier.getIdDossier(),
                """
                Bonjour %s,

                Votre dossier d'affiliation #%s a été clôturé en état abandonné.
                Motif indiqué : %s

                Aucun compte commerçant ni contrat n'a été créé pour ce dossier.

                Cordialement,
                L'équipe Lana Cash
                """.formatted(merchantName, dossier.getIdDossier(), safe(motif))
            );
        }

        notifyCommercialDossierAbandoned(dossier, motif, authenticatedCommerciale, merchantName);
        notifyBackOfficeDossierAbandoned(dossier, motif, merchantName);
        notifySupervisorsDossierAbandoned(dossier, motif, merchantName);
    }

    private void notifyCommercialDossierAbandoned(
        dossier_affiliation dossier,
        String motif,
        commerciale authenticatedCommerciale,
        String merchantName
    ) {
        utilisateur commercialUser = authenticatedCommerciale == null ? null : authenticatedCommerciale.getUtilisateur();
        if (commercialUser == null) {
            return;
        }
        String commercialDisplayName = resolveCommercialDisplayName(authenticatedCommerciale);
        String message = "Le dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été abandonné. Motif: "
            + safe(motif);
        createNotification(
            commercialUser,
            dossier.getIdDossier(),
            message,
            TypeNotification.DOSSIER_ABANDONNE
        );
        affiliationStatusMailService.sendStatusUpdateEmail(
            commercialUser.getEmail(),
            commercialDisplayName,
            "Dossier abandonné #" + dossier.getIdDossier(),
            """
            Bonjour %s,

            Le dossier #%s de %s est maintenant en état abandonné.
            Motif indiqué : %s

            Aucun compte commerçant, contrat ou conversion ne sera généré pour ce dossier.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(commercialDisplayName, dossier.getIdDossier(), merchantName, safe(motif))
        );
    }

    private void notifyBackOfficeDossierAbandoned(
        dossier_affiliation dossier,
        String motif,
        String merchantName
    ) {
        back_office backOffice = dossier.getBackOffice();
        utilisateur backOfficeUser = backOffice == null ? null : backOffice.getUtilisateur();
        if (backOfficeUser == null) {
            return;
        }
        String backOfficeDisplayName = firstNotBlank(
            (safe(backOffice.getPrenom()) + " " + safe(backOffice.getNom())).trim(),
            backOfficeUser.getEmail()
        );
        String message = "Le dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été abandonné par le commercial. Motif: "
            + safe(motif);
        createNotification(
            backOfficeUser,
            dossier.getIdDossier(),
            message,
            TypeNotification.DOSSIER_ABANDONNE
        );
        affiliationStatusMailService.sendStatusUpdateEmail(
            backOfficeUser.getEmail(),
            backOfficeDisplayName,
            "Dossier abandonné après correction #" + dossier.getIdDossier(),
            """
            Bonjour %s,

            Le dossier #%s de %s, que vous aviez renvoyé pour correction, a été abandonné par le commercial.
            Motif indiqué : %s

            Cordialement,
            L'équipe Lana Cash
            """.formatted(backOfficeDisplayName, dossier.getIdDossier(), merchantName, safe(motif))
        );
    }

    private void notifySupervisorsDossierAbandoned(
        dossier_affiliation dossier,
        String motif,
        String merchantName
    ) {
        String message = "Le dossier #"
            + dossier.getIdDossier()
            + " de "
            + merchantName
            + " a été abandonné. Motif: "
            + safe(motif);
        for (utilisateur supervisor : utilisateurRepository.findAllByRole(RoleUser.SUPERVISEUR)) {
            createNotification(
                supervisor,
                dossier.getIdDossier(),
                message,
                TypeNotification.DOSSIER_ABANDONNE
            );
            affiliationStatusMailService.sendStatusUpdateEmail(
                supervisor.getEmail(),
                firstNotBlank(supervisor.getEmail(), "Superviseur"),
                "Dossier abandonné #" + dossier.getIdDossier(),
                """
                Bonjour,

                Le dossier #%s de %s a été abandonné par le commercial.
                Motif indiqué : %s

                Aucun compte commerçant, contrat ou conversion ne sera généré pour ce dossier.

                Cordialement,
                L'équipe Lana Cash
                """.formatted(dossier.getIdDossier(), merchantName, safe(motif))
            );
        }
    }

    private void publishReviewOutcome(
        dossier_affiliation dossier,
        boolean accepted,
        String motifRefus
    ) {
        commercant commercant = dossier.getCommercant();
        utilisateur merchantUser = commercant == null ? null : commercant.getUtilisateur();
        commerciale commerciale = dossier.getCommerciale();
        utilisateur commercialUser = commerciale == null ? null : commerciale.getUtilisateur();
        String merchantName = resolveMerchantDisplayName(commercant, merchantUser);

        if (merchantUser != null) {
            String merchantMessage = accepted
                ? "Votre dossier #"
                    + dossier.getIdDossier()
                    + " a été validé par le back office. Votre affiliation est maintenant acceptee."
                : "Votre dossier #"
                    + dossier.getIdDossier()
                    + " a été refusé. Motif: "
                    + safe(motifRefus);
            createNotification(
                merchantUser,
                dossier.getIdDossier(),
                merchantMessage,
                accepted ? TypeNotification.DOSSIER_VALIDE : TypeNotification.DOSSIER_REFUSE
            );
            affiliationStatusMailService.sendStatusUpdateEmail(
                merchantUser.getEmail(),
                merchantName,
                "Mise à jour de votre dossier d'affiliation #" + dossier.getIdDossier(),
                buildMerchantEmailBody(dossier, accepted, motifRefus, merchantName)
            );
        }

        if (commercialUser != null) {
            String commercialDisplayName = resolveCommercialDisplayName(commerciale);
            String commercialMessage = accepted
                ? "Le dossier #"
                    + dossier.getIdDossier()
                    + " de "
                    + merchantName
                    + " a été validé par le back office."
                : "Le dossier #"
                    + dossier.getIdDossier()
                    + " de "
                    + merchantName
                    + " a été refusé. Motif: "
                    + safe(motifRefus);
            createNotification(
                commercialUser,
                dossier.getIdDossier(),
                commercialMessage,
                accepted ? TypeNotification.DOSSIER_VALIDE : TypeNotification.DOSSIER_REFUSE
            );
            affiliationStatusMailService.sendStatusUpdateEmail(
                commercialUser.getEmail(),
                commercialDisplayName,
                "Decision du back office sur le dossier d'affiliation #" + dossier.getIdDossier(),
                buildCommercialEmailBody(
                    dossier,
                    accepted,
                    motifRefus,
                    merchantName,
                    commercialDisplayName
                )
            );
        }
    }

    private void createNotification(
        utilisateur utilisateur,
        Long dossierId,
        String message,
        TypeNotification typeNotification
    ) {
        notifications notification = new notifications();
        notification.setUtilisateur(utilisateur);
        notification.setDossierId(dossierId);
        notification.setMessage(message);
        notification.setTypeNotification(typeNotification);
        notification.setDateEnvoi(LocalDate.now());
        notification.setStatutLecture(Boolean.FALSE);
        notificationsRepository.save(notification);
    }

    private void applyNegotiableFields(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        TypeAffiliation typeAffiliation = dossier.getTypeAffiliation();
        if (typeAffiliation == null) {
            throw new IllegalArgumentException("Le type d'affiliation du dossier est introuvable.");
        }

        switch (typeAffiliation) {
            case TPE -> applyTpeNegotiableFields(dossier, request);
            case E_COMMERCE -> applyEcommerceNegotiableFields(dossier, request);
            case ENCAISSEMENT_ET_ECOMMERCE -> {
                applyTpeNegotiableFields(dossier, request);
                applyEcommerceNegotiableFields(dossier, request);
            }
            case SOFTPOS, QR_CODE -> {
                requireText(
                    request.commissionLocaleQrSoftpos(),
                    "La commission locale QR Code / SoftPOS est obligatoire."
                );
                requireText(
                    request.commissionEtrangereQrSoftpos(),
                    "La commission etrangere QR Code / SoftPOS est obligatoire."
                );
                requireText(
                    request.fraisServiceQrSoftpos(),
                    "Les frais de service QR Code / SoftPOS sont obligatoires."
                );
                requireText(request.abonnementPackage(), "L'abonnement est obligatoire.");
                dossier.setCommissionLocaleQrSoftpos(
                    normalize(request.commissionLocaleQrSoftpos())
                );
                dossier.setCommissionEtrangereQrSoftpos(
                    normalize(request.commissionEtrangereQrSoftpos())
                );
                dossier.setFraisServiceQrSoftpos(normalize(request.fraisServiceQrSoftpos()));
                dossier.setConditionsQrSoftpos(normalize(request.conditionsQrSoftpos()));
                dossier.setAbonnementPackage(normalize(request.abonnementPackage()));
            }
        }

        applyServiceOptions(dossier, request);
    }

    private void applyTpeNegotiableFields(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        requireText(request.commissionLocaleTpe(), "La commission locale TPE est obligatoire.");
        requireText(
            request.commissionEtrangereTpe(),
            "La commission etrangere TPE est obligatoire."
        );
        requireText(request.depotTpe(), "Le dépôt TPE est obligatoire.");
        requireText(request.prixAchatTpe(), "Le prix d'achat TPE est obligatoire.");
        requireText(request.prixLicenceTpe(), "Le prix de licence TPE est obligatoire.");
        requireText(request.abonnementPackage(), "L'abonnement est obligatoire.");
        dossier.setCommissionLocaleTpe(normalize(request.commissionLocaleTpe()));
        dossier.setCommissionEtrangereTpe(normalize(request.commissionEtrangereTpe()));
        dossier.setDepotTpe(normalize(request.depotTpe()));
        dossier.setPrixAchatTpe(normalize(request.prixAchatTpe()));
        dossier.setPrixLicenceTpe(normalize(request.prixLicenceTpe()));
        dossier.setAbonnementPackage(normalize(request.abonnementPackage()));
    }

    private void applyEcommerceNegotiableFields(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        requireText(
            request.commissionLocaleEcommerce(),
            "La commission locale e-commerce est obligatoire."
        );
        requireText(
            request.commissionEtrangereEcommerce(),
            "La commission etrangere e-commerce est obligatoire."
        );
        requireText(
            request.fraisMiseEnServiceEcommerce(),
            "Les frais de mise en service e-commerce sont obligatoires."
        );
        dossier.setCommissionLocaleEcommerce(normalize(request.commissionLocaleEcommerce()));
        dossier.setCommissionEtrangereEcommerce(normalize(request.commissionEtrangereEcommerce()));
        dossier.setFraisMiseEnServiceEcommerce(normalize(request.fraisMiseEnServiceEcommerce()));
    }

    private void applyCommercialReportFields(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        requireText(
            request.compteRenduQualification(),
            "La qualification du compte-rendu commercial est obligatoire."
        );
        if ("AFFILIE".equalsIgnoreCase(normalize(request.compteRenduQualification()))) {
            requireText(
                request.compteRenduAcquereur(),
                "L'acquereur est obligatoire pour un commerçant déjà affilié."
            );
        }
        String compteRenduOrigineProspect = resolveCompteRenduOrigineProspect(dossier, request);
        requireText(compteRenduOrigineProspect, "L'origine du prospect est obligatoire.");
        requireText(
            request.compteRenduContactNomPrenom(),
            "Le nom et prénom du contact sont obligatoires."
        );
        requireText(
            request.compteRenduContactFonction(),
            "La fonction du contact est obligatoire."
        );
        requireText(
            request.compteRenduPointVenteAcronyme(),
            "L'acronyme du point de vente est obligatoire."
        );
        requireText(
            request.compteRenduCommercant(),
            "Le nom du commerçant renseigne dans le compte-rendu est obligatoire."
        );
        requireText(
            request.compteRenduDateOuverture(),
            "La date d'ouverture est obligatoire."
        );
        requireText(
            request.compteRenduNombreEmployes(),
            "Le nombre d'employes est obligatoire."
        );
        requireText(
            request.compteRenduActivite(),
            "L'activité du commerçant est obligatoire."
        );
        String compteRenduMcc = firstNotBlank(request.compteRenduMcc(), request.mcc());
        requireText(compteRenduMcc, "Le MCC est obligatoire.");
        requireText(
            request.compteRenduNatureMarchandises(),
            "La nature des marchandises ou services est obligatoire."
        );
        requireText(
            request.compteRenduSuperficieLocal(),
            "La superficie du local est obligatoire."
        );
        requireText(
            request.compteRenduStatutLocal(),
            "Le statut du local est obligatoire."
        );
        requireText(
            request.compteRenduChiffreAffairesAnnuel(),
            "Le chiffre d'affaires annuel est obligatoire."
        );
        requireText(
            request.compteRenduPartPaiementCarte(),
            "La part estimee des paiements par cartes est obligatoire."
        );
        requireText(
            request.compteRenduPartCarteLocale(),
            "La part des cartes locales est obligatoire."
        );
        requireText(
            request.compteRenduProfilCommercant(),
            "Le profil du commerçant est obligatoire."
        );
        requireText(
            request.compteRenduAppreciationVisite(),
            "L'appreciation generale de la visite est obligatoire."
        );
        requireText(request.compteRenduFaitA(), "Le lieu du compte-rendu est obligatoire.");
        requireText(request.compteRenduDateVisite(), "La date du compte-rendu est obligatoire.");

        dossier.setCompteRenduQualification(normalize(request.compteRenduQualification()));
        dossier.setCompteRenduAcquereur(normalize(request.compteRenduAcquereur()));
        dossier.setCompteRenduOrigineProspect(compteRenduOrigineProspect);
        dossier.setCompteRenduOrigineProspectDetail(
            normalize(request.compteRenduOrigineProspectDetail())
        );
        dossier.setCompteRenduContactNomPrenom(
            normalize(request.compteRenduContactNomPrenom())
        );
        dossier.setCompteRenduContactFonction(normalize(request.compteRenduContactFonction()));
        dossier.setCompteRenduPointVenteAcronyme(
            normalize(request.compteRenduPointVenteAcronyme())
        );
        dossier.setCompteRenduActionnaires(normalize(request.compteRenduActionnaires()));
        dossier.setCompteRenduCommercant(normalize(request.compteRenduCommercant()));
        dossier.setCompteRenduChaine(normalize(request.compteRenduChaine()));
        dossier.setCompteRenduRelationsLc(normalize(request.compteRenduRelationsLc()));
        dossier.setCompteRenduDateOuverture(normalize(request.compteRenduDateOuverture()));
        dossier.setCompteRenduNombreEmployes(normalize(request.compteRenduNombreEmployes()));
        dossier.setCompteRenduActivite(normalize(request.compteRenduActivite()));
        dossier.setCompteRenduMcc(normalize(compteRenduMcc));
        dossier.setCompteRenduStandingMagasin(
            normalize(request.compteRenduStandingMagasin())
        );
        dossier.setCompteRenduNatureMarchandises(
            normalize(request.compteRenduNatureMarchandises())
        );
        dossier.setCompteRenduSuperficieLocal(
            normalize(request.compteRenduSuperficieLocal())
        );
        dossier.setCompteRenduStatutLocal(normalize(request.compteRenduStatutLocal()));
        dossier.setCompteRenduChiffreAffairesAnnuel(
            normalize(request.compteRenduChiffreAffairesAnnuel())
        );
        dossier.setCompteRenduPartPaiementCarte(
            normalize(request.compteRenduPartPaiementCarte())
        );
        dossier.setCompteRenduPartCarteLocale(normalize(request.compteRenduPartCarteLocale()));
        dossier.setCompteRenduProfilCommercant(
            normalize(request.compteRenduProfilCommercant())
        );
        dossier.setCompteRenduAppreciationVisite(
            normalize(request.compteRenduAppreciationVisite())
        );
        dossier.setCompteRenduCommentaire(normalize(request.compteRenduCommentaire()));
        dossier.setCompteRenduFaitA(normalize(request.compteRenduFaitA()));
        dossier.setCompteRenduDateVisite(normalize(request.compteRenduDateVisite()));

        // ── Double écriture vers la table normalisée comptes_rendus ───────────
        compte_rendu cr = new compte_rendu();
        cr.setDossier(dossier);
        cr.setCommerciale(dossier.getCommerciale());
        cr.setQualification(normalize(request.compteRenduQualification()));
        cr.setAcquereur(normalize(request.compteRenduAcquereur()));
        cr.setOrigineProspect(normalize(resolveCompteRenduOrigineProspect(dossier, request)));
        cr.setOrigineProspectDetail(normalize(request.compteRenduOrigineProspectDetail()));
        cr.setContactNomPrenom(normalize(request.compteRenduContactNomPrenom()));
        cr.setContactFonction(normalize(request.compteRenduContactFonction()));
        cr.setPointVenteAcronyme(normalize(request.compteRenduPointVenteAcronyme()));
        cr.setActionnaires(normalize(request.compteRenduActionnaires()));
        cr.setNomCommercant(normalize(request.compteRenduCommercant()));
        cr.setChaine(normalize(request.compteRenduChaine()));
        cr.setRelationsLc(normalize(request.compteRenduRelationsLc()));
        cr.setDateOuverture(normalize(request.compteRenduDateOuverture()));
        cr.setNombreEmployes(normalize(request.compteRenduNombreEmployes()));
        cr.setActivite(normalize(request.compteRenduActivite()));
        cr.setMcc(normalize(compteRenduMcc));
        cr.setStandingMagasin(normalize(request.compteRenduStandingMagasin()));
        cr.setNatureMarchandises(normalize(request.compteRenduNatureMarchandises()));
        cr.setSuperficieLocal(normalize(request.compteRenduSuperficieLocal()));
        cr.setStatutLocal(normalize(request.compteRenduStatutLocal()));
        cr.setChiffreAffairesAnnuel(normalize(request.compteRenduChiffreAffairesAnnuel()));
        cr.setPartPaiementCarte(normalize(request.compteRenduPartPaiementCarte()));
        cr.setPartCarteLocale(normalize(request.compteRenduPartCarteLocale()));
        cr.setProfilCommercant(normalize(request.compteRenduProfilCommercant()));
        cr.setAppreciationVisite(normalize(request.compteRenduAppreciationVisite()));
        cr.setCommentaire(normalize(request.compteRenduCommentaire()));
        cr.setFaitA(normalize(request.compteRenduFaitA()));
        cr.setDateVisite(normalize(request.compteRenduDateVisite()));
        compteRenduRepository.save(cr);
        // ─────────────────────────────────────────────────────────────────────
    }

    private void applyMerchantMcc(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        if (dossier.getCommercant() == null) {
            return;
        }
        String mcc = normalize(firstNotBlank(request.mcc(), request.compteRenduMcc()));
        if (StringUtils.hasText(mcc)) {
            dossier.getCommercant().setMcc(mcc);
        }
    }

    private commerciale readAuthenticatedCommerciale(String authorizationHeader) {
        utilisateur authenticatedUser = readAuthenticatedStaff(
            authorizationHeader,
            RoleUser.COMMERCIAL
        );
        return commercialeRepository
            .findByUtilisateur_Id(authenticatedUser.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte commercial introuvable."
                )
            );
    }

    private void applyMerchantFields(
        commercant commercant,
        CommercialAffiliationDraftRequest request,
        TypeCommercant typeCommercant
    ) {
        commercant.setType(typeCommercant);
        commercant.setNomCommercial(resolveDisplayName(request, typeCommercant));
        commercant.setRaisonSociale(resolveLegalName(request, typeCommercant));
        commercant.setRegistreCommerce(normalize(request.getRc()));
        commercant.setIdentifiantFiscal(normalize(request.getIce()));
        commercant.setAdresse(normalize(request.getAdresse()));
        commercant.setVille(normalize(request.getVille()));
        commercant.setRegion(normalize(request.getRegion()));
        commercant.setTelephone(normalize(request.getTelephonePrincipal()));
        commercant.setTelephoneSecondaire(normalize(request.getTelephoneSecondaire()));
        commercant.setEmailContact(normalize(request.getEmail()));
        commercant.setActivite(normalize(request.getActivite()));
        commercant.setSecteur(normalize(request.getSecteur()));
        commercant.setMcc(normalize(request.getMcc()));
        commercant.setChainePointVente(normalize(request.getChainePointVente()));
        commercant.setPatente(normalize(request.getPatente()));
        commercant.setFonction(normalize(request.getFonction()));
        commercant.setBeneficiairesEffectifs(normalize(request.getBeneficiairesEffectifs()));
        commercant.setDateNaissance(normalize(request.getDateNaissance()));
        commercant.setNationalite(normalize(request.getNationalite()));
        commercant.setNombrePointsVente(
            parseOptionalIntegerInRange(
                request.getNombrePointsVente(),
                "Le nombre de points de vente",
                1,
                MAX_POINTS_VENTE
            )
        );
    }

    private void applyDossierBaseFields(
        dossier_affiliation dossier,
        CommercialAffiliationDraftRequest request
    ) {
        dossier.setRib(normalize(request.getRib()));
        dossier.setModeMiseADispositionTpe(normalize(request.getModeMiseADispositionTpe()));
        dossier.setNombreTpe(
            parseOptionalIntegerInRange(request.getNombreTpe(), "Le nombre de TPE", 1, MAX_TPE)
        );
        dossier.setEquipementTpe(normalize(request.getEquipementTpe()));
        dossier.setConnectiviteTpe(normalize(request.getConnectiviteTpe()));
        dossier.setModeServiceEcommerce(normalize(request.getModeServiceEcommerce()));
        dossier.setSiteMarchandUrl(normalize(request.getSiteMarchandUrl()));
        dossier.setApplicationMobile(normalize(request.getApplicationMobile()));
        dossier.setModeleQrSoftpos(normalize(request.getModeleQrSoftpos()));
        dossier.setServiceCreditVoucher(Boolean.TRUE.equals(request.getServiceCreditVoucher()));
        dossier.setServiceAnnulation(Boolean.TRUE.equals(request.getServiceAnnulation()));
        dossier.setServiceDcc(Boolean.TRUE.equals(request.getServiceDcc()));
        dossier.setServicePreAutorisationCartePresente(
            Boolean.TRUE.equals(request.getServicePreAutorisationCartePresente())
        );
        dossier.setServicePreAutorisationCartePresenteConfirmationManuelle(
            Boolean.TRUE.equals(request.getServicePreAutorisationCartePresenteConfirmationManuelle())
        );
        dossier.setServicePreAutorisationManuelleConfirmationCartePresente(
            Boolean.TRUE.equals(request.getServicePreAutorisationManuelleConfirmationCartePresente())
        );
        dossier.setServiceTransactionManuelle(Boolean.TRUE.equals(request.getServiceTransactionManuelle()));
        dossier.setServiceTransactionManuelleSansCvv(
            Boolean.TRUE.equals(request.getServiceTransactionManuelleSansCvv())
        );
    }

    private void savePointVentes(
        CommercialAffiliationDraftRequest request,
        commercant commercant
    ) {
        Long commercantId = commercant.getIdCommercant();
        if (commercantId != null) {
            pdvRepository.deleteAll(pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(commercantId));
        }

        if (mapAffiliationType(request.getTypeAffiliation()) == TypeAffiliation.E_COMMERCE) {
            return;
        }

        for (PointVenteRequest pointVenteRequest : readPointVenteRequests(request.getPointVentesJson())) {
            pdv pointVente = new pdv();
            pointVente.setNomPDV(normalize(pointVenteRequest.nom()));
            pointVente.setAdresse(normalize(pointVenteRequest.adresse()));
            pointVente.setVille(normalize(pointVenteRequest.ville()));
            pointVente.setCodePostal(normalize(pointVenteRequest.codePostal()));
            pointVente.setQuartier(normalize(pointVenteRequest.quartier()));
            pointVente.setTelephone(normalize(pointVenteRequest.telephone()));
            pointVente.setEmail(normalize(pointVenteRequest.email()));
            pointVente.setStatut("EN_ATTENTE");
            pointVente.setDateCreation(LocalDate.now());
            pointVente.setCommercant(commercant);
            pdvRepository.save(pointVente);
            // Géocodage en arrière-plan (best-effort, jamais bloquant) : voir
            // PdvGeocodingService pour le detail des enchainements d'appels a
            // Nominatim qui rendaient la creation/correction de la demande lente.
            pdvGeocodingService.geocoderEtMettreAJour(
                pointVente.getIdPDV(),
                pointVente.getAdresse(),
                pointVente.getQuartier(),
                pointVente.getVille(),
                pointVente.getCodePostal()
            );
        }
    }

    private List<PointVenteRequest> readPointVenteRequests(String pointVentesJson) {
        if (!StringUtils.hasText(pointVentesJson)) {
            return List.of();
        }

        try {
            List<Map<String, String>> rawPointVentes = objectMapper.readValue(
                pointVentesJson,
                new TypeReference<List<Map<String, String>>>() {}
            );
            return rawPointVentes
                .stream()
                .map(
                    item ->
                        new PointVenteRequest(
                            item.get("nom"),
                            item.get("adresse"),
                            item.get("ville"),
                            item.get("codePostal"),
                            item.get("quartier"),
                            firstNotBlank(item.get("téléphone"), item.get("telephone")),
                            firstNotBlank(item.get("e-mail"), item.get("email"))
                        )
                )
                .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Les informations des points de vente sont invalides.");
        }
    }

    private int saveCommercialDocuments(
        CommercialAffiliationDraftRequest request,
        dossier_affiliation dossier,
        Map<String, MultipartFile> uploadedDocuments
    ) {
        int savedDocuments = 0;
        Path dossierDirectory = uploadRoot.resolve("dossier-" + dossier.getIdDossier());

        try {
            Files.createDirectories(dossierDirectory);

            for (Map.Entry<String, MultipartFile> entry : uploadedDocuments.entrySet()) {
                MultipartFile file = entry.getValue();
                if (file == null || file.isEmpty()) {
                    continue;
                }

                documentMimeValidator.validate(file);

                String documentKey = entry.getKey();
                String storedFilename = documentKey + "-" + System.currentTimeMillis() + "-"
                    + sanitizeFileName(file.getOriginalFilename());
                Path destination = dossierDirectory.resolve(storedFilename);
                file.transferTo(destination.toFile());

                documents document = new documents();
                document.setDossierAffiliation(dossier);
                document.setTypeDocument(mapDocumentType(request.getTypeCommercant(), documentKey));
                document.setCheminStockage(destination.toString());
                document.setTailleFichier(file.getSize());
                document.setDateUpload(LocalDate.now());
                document.setStatutDocument(StatusDocument.UPLOADE);
                documentsRepository.save(document);
                savedDocuments++;
            }

            if (savedDocuments == 0) {
                savedDocuments += saveCommercialDocumentMetadataOnly(request, dossier);
            }

            return savedDocuments;
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible d'enregistrer les documents du dossier.", exception);
        }
    }

    private int saveCommercialDocumentMetadataOnly(
        CommercialAffiliationDraftRequest request,
        dossier_affiliation dossier
    ) {
        int savedDocuments = 0;
        for (DocumentDefinition documentDefinition : documentDefinitions(request.getTypeCommercant())) {
            String documentName = normalize(documentNameFromRequest(request, documentDefinition.key()));
            if (!StringUtils.hasText(documentName)) {
                continue;
            }

            documents document = new documents();
            document.setDossierAffiliation(dossier);
            document.setTypeDocument(documentDefinition.typeDocument());
            document.setCheminStockage(documentName);
            document.setTailleFichier(0L);
            document.setDateUpload(LocalDate.now());
            document.setStatutDocument(StatusDocument.UPLOADE);
            documentsRepository.save(document);
            savedDocuments++;
        }
        return savedDocuments;
    }

    private Iterable<DocumentDefinition> documentDefinitions(String merchantType) {
        TypeCommercant typeCommercant = mapMerchantType(merchantType);

        return switch (typeCommercant) {
            case PERSONNE_PHYSIQUE -> List.of(
                new DocumentDefinition("cinDocument", TypeDocument.PIECE_IDENTITE),
                new DocumentDefinition("ribDocument", TypeDocument.RIB),
                new DocumentDefinition("patenteDocument", TypeDocument.INSCRIPTION_PATENTE)
            );
            case PERSONNE_MORALE -> List.of(
                new DocumentDefinition("statutsDocument", TypeDocument.STATUTS_SOCIETE),
                new DocumentDefinition("rcDocument", TypeDocument.REGISTRE_COMMERCE),
                new DocumentDefinition("iceDocument", TypeDocument.ICE),
                new DocumentDefinition("cinRepresentantDocument", TypeDocument.CIN_REPRESENTANT_LEGAL),
                new DocumentDefinition("pvNominationDocument", TypeDocument.PV_NOMINATION),
                new DocumentDefinition("ribDocument", TypeDocument.RIB)
            );
            case AUTO_ENTREPRENEUR -> List.of(
                new DocumentDefinition("cinDocument", TypeDocument.PIECE_IDENTITE),
                new DocumentDefinition("attestationAeDocument", TypeDocument.ATTESTATION_AUTO_ENTREPRENEUR),
                new DocumentDefinition("ribDocument", TypeDocument.RIB)
            );
            case ASSOCIATION_FONDATION -> List.of(
                new DocumentDefinition("cinSignataireDocument", TypeDocument.CIN_SIGNATAIRE),
                new DocumentDefinition("pvAssociationDocument", TypeDocument.PV_ASSOCIATION),
                new DocumentDefinition("statutsDocument", TypeDocument.STATUTS_ASSOCIATION),
                new DocumentDefinition("listeMembresDocument", TypeDocument.LISTE_MEMBRES),
                new DocumentDefinition("ribDocument", TypeDocument.RIB)
            );
        };
    }

    private String documentNameFromRequest(CommercialAffiliationDraftRequest request, String documentKey) {
        return switch (documentKey) {
            case "cinDocument" -> request.getCinDocumentName();
            case "ribDocument" -> request.getRibDocumentName();
            case "patenteDocument" -> request.getPatenteDocumentName();
            case "statutsDocument" -> request.getStatutsDocumentName();
            case "rcDocument" -> request.getRcDocumentName();
            case "iceDocument" -> request.getIceDocumentName();
            case "cinRepresentantDocument" -> request.getCinRepresentantDocumentName();
            case "pvNominationDocument" -> request.getPvNominationDocumentName();
            case "attestationAeDocument" -> request.getAttestationAeDocumentName();
            case "cinSignataireDocument" -> request.getCinSignataireDocumentName();
            case "pvAssociationDocument" -> request.getPvAssociationDocumentName();
            case "listeMembresDocument" -> request.getListeMembresDocumentName();
            default -> null;
        };
    }

    private TypeDocument mapDocumentType(String merchantType, String documentKey) {
        return switch (documentKey) {
            case "cinDocument" -> TypeDocument.PIECE_IDENTITE;
            case "ribDocument" -> TypeDocument.RIB;
            case "patenteDocument" -> TypeDocument.INSCRIPTION_PATENTE;
            case "statutsDocument" ->
                mapMerchantType(merchantType) == TypeCommercant.ASSOCIATION_FONDATION
                    ? TypeDocument.STATUTS_ASSOCIATION
                    : TypeDocument.STATUTS_SOCIETE;
            case "rcDocument" -> TypeDocument.REGISTRE_COMMERCE;
            case "iceDocument" -> TypeDocument.ICE;
            case "cinRepresentantDocument" -> TypeDocument.CIN_REPRESENTANT_LEGAL;
            case "pvNominationDocument" -> TypeDocument.PV_NOMINATION;
            case "attestationAeDocument" -> TypeDocument.ATTESTATION_AUTO_ENTREPRENEUR;
            case "cinSignataireDocument" -> TypeDocument.CIN_SIGNATAIRE;
            case "pvAssociationDocument" -> TypeDocument.PV_ASSOCIATION;
            case "listeMembresDocument" -> TypeDocument.LISTE_MEMBRES;
            default -> throw new IllegalArgumentException("Type de document inconnu: " + documentKey);
        };
    }

    private void applyServiceOptions(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        dossier.setServiceCreditVoucher(Boolean.TRUE.equals(request.serviceCreditVoucher()));
        dossier.setServiceAnnulation(Boolean.TRUE.equals(request.serviceAnnulation()));
        dossier.setServiceDcc(Boolean.TRUE.equals(request.serviceDcc()));
        dossier.setServicePreAutorisationCartePresente(
            Boolean.TRUE.equals(request.servicePreAutorisationCartePresente())
        );
        dossier.setServicePreAutorisationCartePresenteConfirmationManuelle(
            Boolean.TRUE.equals(request.servicePreAutorisationCartePresenteConfirmationManuelle())
        );
        dossier.setServicePreAutorisationManuelleConfirmationCartePresente(
            Boolean.TRUE.equals(request.servicePreAutorisationManuelleConfirmationCartePresente())
        );
        dossier.setServiceTransactionManuelle(Boolean.TRUE.equals(request.serviceTransactionManuelle()));
        dossier.setServiceTransactionManuelleSansCvv(
            Boolean.TRUE.equals(request.serviceTransactionManuelleSansCvv())
        );
    }

    private void applyNegotiableFieldsWithoutValidation(
        dossier_affiliation dossier,
        CommercialAffiliationDraftRequest request
    ) {
        dossier.setCommissionLocaleTpe(normalize(request.getCommissionLocaleTpe()));
        dossier.setCommissionEtrangereTpe(normalize(request.getCommissionEtrangereTpe()));
        dossier.setDepotTpe(normalize(request.getDepotTpe()));
        dossier.setPrixAchatTpe(normalize(request.getPrixAchatTpe()));
        dossier.setPrixLicenceTpe(normalize(request.getPrixLicenceTpe()));
        dossier.setAbonnementPackage(normalize(request.getAbonnementPackage()));
        dossier.setCommissionLocaleEcommerce(normalize(request.getCommissionLocaleEcommerce()));
        dossier.setCommissionEtrangereEcommerce(normalize(request.getCommissionEtrangereEcommerce()));
        dossier.setFraisMiseEnServiceEcommerce(normalize(request.getFraisMiseEnServiceEcommerce()));
        dossier.setCommissionLocaleQrSoftpos(normalize(request.getCommissionLocaleQrSoftpos()));
        dossier.setCommissionEtrangereQrSoftpos(normalize(request.getCommissionEtrangereQrSoftpos()));
        dossier.setFraisServiceQrSoftpos(normalize(request.getFraisServiceQrSoftpos()));
        dossier.setConditionsQrSoftpos(normalize(request.getConditionsQrSoftpos()));
    }

    private void applyCommercialReportFieldsWithoutValidation(
        dossier_affiliation dossier,
        CommercialAffiliationDraftRequest request
    ) {
        dossier.setCompteRenduQualification(normalize(request.getCompteRenduQualification()));
        dossier.setCompteRenduAcquereur(normalize(request.getCompteRenduAcquereur()));
        dossier.setCompteRenduOrigineProspect(normalize(request.getCompteRenduOrigineProspect()));
        dossier.setCompteRenduOrigineProspectDetail(normalize(request.getCompteRenduOrigineProspectDetail()));
        dossier.setCompteRenduContactNomPrenom(normalize(request.getCompteRenduContactNomPrenom()));
        dossier.setCompteRenduContactFonction(normalize(request.getCompteRenduContactFonction()));
        dossier.setCompteRenduPointVenteAcronyme(normalize(request.getCompteRenduPointVenteAcronyme()));
        dossier.setCompteRenduActionnaires(normalize(request.getCompteRenduActionnaires()));
        dossier.setCompteRenduCommercant(normalize(request.getCompteRenduCommercant()));
        dossier.setCompteRenduChaine(normalize(request.getCompteRenduChaine()));
        dossier.setCompteRenduRelationsLc(normalize(request.getCompteRenduRelationsLc()));
        dossier.setCompteRenduDateOuverture(normalize(request.getCompteRenduDateOuverture()));
        dossier.setCompteRenduNombreEmployes(normalize(request.getCompteRenduNombreEmployes()));
        dossier.setCompteRenduActivite(normalize(request.getCompteRenduActivite()));
        dossier.setCompteRenduMcc(normalize(request.getCompteRenduMcc()));
        dossier.setCompteRenduStandingMagasin(normalize(request.getCompteRenduStandingMagasin()));
        dossier.setCompteRenduNatureMarchandises(normalize(request.getCompteRenduNatureMarchandises()));
        dossier.setCompteRenduSuperficieLocal(normalize(request.getCompteRenduSuperficieLocal()));
        dossier.setCompteRenduStatutLocal(normalize(request.getCompteRenduStatutLocal()));
        dossier.setCompteRenduChiffreAffairesAnnuel(normalize(request.getCompteRenduChiffreAffairesAnnuel()));
        dossier.setCompteRenduPartPaiementCarte(normalize(request.getCompteRenduPartPaiementCarte()));
        dossier.setCompteRenduPartCarteLocale(normalize(request.getCompteRenduPartCarteLocale()));
        dossier.setCompteRenduProfilCommercant(normalize(request.getCompteRenduProfilCommercant()));
        dossier.setCompteRenduAppreciationVisite(normalize(request.getCompteRenduAppreciationVisite()));
        dossier.setCompteRenduCommentaire(normalize(request.getCompteRenduCommentaire()));
        dossier.setCompteRenduFaitA(normalize(request.getCompteRenduFaitA()));
        dossier.setCompteRenduDateVisite(normalize(request.getCompteRenduDateVisite()));

        // ── Double écriture vers la table normalisée comptes_rendus (brouillon) ─
        compte_rendu cr = new compte_rendu();
        cr.setDossier(dossier);
        cr.setCommerciale(dossier.getCommerciale());
        cr.setQualification(normalize(request.getCompteRenduQualification()));
        cr.setAcquereur(normalize(request.getCompteRenduAcquereur()));
        cr.setOrigineProspect(normalize(request.getCompteRenduOrigineProspect()));
        cr.setOrigineProspectDetail(normalize(request.getCompteRenduOrigineProspectDetail()));
        cr.setContactNomPrenom(normalize(request.getCompteRenduContactNomPrenom()));
        cr.setContactFonction(normalize(request.getCompteRenduContactFonction()));
        cr.setPointVenteAcronyme(normalize(request.getCompteRenduPointVenteAcronyme()));
        cr.setActionnaires(normalize(request.getCompteRenduActionnaires()));
        cr.setNomCommercant(normalize(request.getCompteRenduCommercant()));
        cr.setChaine(normalize(request.getCompteRenduChaine()));
        cr.setRelationsLc(normalize(request.getCompteRenduRelationsLc()));
        cr.setDateOuverture(normalize(request.getCompteRenduDateOuverture()));
        cr.setNombreEmployes(normalize(request.getCompteRenduNombreEmployes()));
        cr.setActivite(normalize(request.getCompteRenduActivite()));
        cr.setMcc(normalize(request.getCompteRenduMcc()));
        cr.setStandingMagasin(normalize(request.getCompteRenduStandingMagasin()));
        cr.setNatureMarchandises(normalize(request.getCompteRenduNatureMarchandises()));
        cr.setSuperficieLocal(normalize(request.getCompteRenduSuperficieLocal()));
        cr.setStatutLocal(normalize(request.getCompteRenduStatutLocal()));
        cr.setChiffreAffairesAnnuel(normalize(request.getCompteRenduChiffreAffairesAnnuel()));
        cr.setPartPaiementCarte(normalize(request.getCompteRenduPartPaiementCarte()));
        cr.setPartCarteLocale(normalize(request.getCompteRenduPartCarteLocale()));
        cr.setProfilCommercant(normalize(request.getCompteRenduProfilCommercant()));
        cr.setAppreciationVisite(normalize(request.getCompteRenduAppreciationVisite()));
        cr.setCommentaire(normalize(request.getCompteRenduCommentaire()));
        cr.setFaitA(normalize(request.getCompteRenduFaitA()));
        cr.setDateVisite(normalize(request.getCompteRenduDateVisite()));
        if (dossier.getIdDossier() != null) {
            // Sauvegarder uniquement si le dossier est déjà persisté
            compteRenduRepository.save(cr);
        }
        // ─────────────────────────────────────────────────────────────────────
    }

    private void saveOrUpdateSpecificMerchantProfile(
        CommercialAffiliationDraftRequest request,
        commercant commercant
    ) {
        Long commercantId = commercant.getIdCommercant();
        switch (commercant.getType()) {
            case PERSONNE_PHYSIQUE -> {
                PP pp = commercantId == null
                    ? new PP()
                    : ppRepository.findByCommercant_IdCommercant(commercantId).orElseGet(PP::new);
                pp.setNom(normalize(request.getNom()));
                pp.setPrenom(normalize(request.getPrenom()));
                pp.setCin(normalize(request.getCin()));
                pp.setCommercant(commercant);
                ppRepository.save(pp);
            }
            case PERSONNE_MORALE -> {
                PM pm = commercantId == null
                    ? new PM()
                    : pmRepository.findByCommercant_IdCommercant(commercantId).orElseGet(PM::new);
                pm.setRaisonSociale(normalize(request.getRaisonSociale()));
                pm.setRegistreCommerce(normalize(request.getRc()));
                pm.setIce(normalize(request.getIce()));
                pm.setFormeJuridique(normalize(request.getFormeJuridique()));
                pm.setRepresentantLegal(normalize(request.getRepresentantLegal()));
                pm.setCommercant(commercant);
                pmRepository.save(pm);
            }
            case AUTO_ENTREPRENEUR -> {
                AE ae = commercantId == null
                    ? new AE()
                    : aeRepository.findByCommercant_IdCommercant(commercantId).orElseGet(AE::new);
                ae.setNom(normalize(request.getNom()));
                ae.setPrenom(normalize(request.getPrenom()));
                ae.setNumeroAutoEntrepreneur(normalize(request.getNumeroAutoEntrepreneur()));
                ae.setCommercant(commercant);
                aeRepository.save(ae);
            }
            case ASSOCIATION_FONDATION -> {
                Association association = commercantId == null
                    ? new Association()
                    : associationRepository
                        .findByCommercant_IdCommercant(commercantId)
                        .orElseGet(Association::new);
                association.setNomEntite(normalize(request.getNomEntite()));
                association.setRepresentantLegal(normalize(request.getRepresentantLegal()));
                association.setObjet(normalize(request.getObjet()));
                association.setCommercant(commercant);
                associationRepository.save(association);
            }
        }
    }

    private TypeCommercant mapMerchantType(String value) {
        requireText(value, "Le type de commerçant est obligatoire.");
        try {
            return TypeCommercant.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Type de commerçant invalide.");
        }
    }

    private TypeAffiliation mapAffiliationType(String value) {
        requireText(value, "Le type d'affiliation est obligatoire.");
        try {
            return TypeAffiliation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Type d'affiliation invalide.");
        }
    }

    private String resolveDisplayName(
        CommercialAffiliationDraftRequest request,
        TypeCommercant typeCommercant
    ) {
        return switch (typeCommercant) {
            case PERSONNE_MORALE -> firstNotBlank(request.getRaisonSociale(), request.getNom());
            case ASSOCIATION_FONDATION -> firstNotBlank(request.getNomEntite(), request.getNom());
            default -> firstNotBlank(request.getNom(), request.getRaisonSociale(), request.getNomEntite());
        };
    }

    private String resolveLegalName(
        CommercialAffiliationDraftRequest request,
        TypeCommercant typeCommercant
    ) {
        return switch (typeCommercant) {
            case PERSONNE_MORALE -> firstNotBlank(request.getRaisonSociale(), request.getNom());
            case ASSOCIATION_FONDATION -> firstNotBlank(request.getNomEntite(), request.getNom());
            default -> firstNotBlank(request.getNom(), request.getRaisonSociale());
        };
    }

    private Integer parseOptionalInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La valeur numerique est invalide.");
        }
    }

    private Integer parseOptionalIntegerInRange(String value, String fieldLabel, int min, int max) {
        Integer parsed = parseOptionalInteger(value);
        if (parsed == null) {
            return null;
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(
                fieldLabel + " doit etre compris entre " + min + " et " + max + "."
            );
        }
        return parsed;
    }

    private String resolveDraftEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "draft-" + UUID.randomUUID() + "@commercial.local";
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (utilisateurRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet e-mail.");
        }
        return normalizedEmail;
    }

    private void updateDraftEmail(utilisateur merchantUser, String email) {
        if (!StringUtils.hasText(email)) {
            if (!StringUtils.hasText(merchantUser.getEmail())) {
                merchantUser.setEmail(resolveDraftEmail(null));
            }
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (
            !normalizedEmail.equalsIgnoreCase(merchantUser.getEmail())
                && utilisateurRepository.existsByEmailIgnoreCase(normalizedEmail)
        ) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet e-mail.");
        }
        merchantUser.setEmail(normalizedEmail);
    }

    private void validateDraftEligibility(
        dossier_affiliation dossier,
        commerciale authenticatedCommerciale
    ) {
        if (
            dossier.getStatus() != StatusDossier.BROUILLON
                && dossier.getStatus() != StatusDossier.SOUMIS
                && dossier.getStatus() != StatusDossier.INCOMPLET
        ) {
            throw new IllegalArgumentException("Ce dossier ne peut plus etre enregistré en brouillon.");
        }
        if (
            dossier.getCommerciale() != null
                && authenticatedCommerciale.getIdCommercial() != null
                && !Objects.equals(
                    dossier.getCommerciale().getIdCommercial(),
                    authenticatedCommerciale.getIdCommercial()
                )
        ) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Ce brouillon appartient a une autre commerciale."
            );
        }
    }

    private boolean isCommercialDirectDossier(dossier_affiliation dossier) {
        return "COMMERCIAL_DIRECT".equalsIgnoreCase(normalize(dossier.getOrigineCreation()));
    }

    private boolean isNewPdvProductRequest(dossier_affiliation dossier) {
        return "NOUVEAU_PDV".equalsIgnoreCase(normalize(dossier.getOrigineCreation()));
    }

    private Optional<dossier_affiliation> findAcceptedPrincipalDossier(commercant commercant) {
        if (commercant == null || commercant.getIdCommercant() == null) {
            return Optional.empty();
        }

        return dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
                commercant.getIdCommercant()
            )
            .stream()
            .filter(dossier -> !isNewPdvProductRequest(dossier))
            .filter(dossier -> dossier.getStatus() == StatusDossier.ACCEPTE)
            .findFirst();
    }

    private Integer countExtensionRequests(commercant commercant) {
        if (commercant == null || commercant.getIdCommercant() == null) {
            return 0;
        }

        return (int) dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
                commercant.getIdCommercant()
            )
            .stream()
            .filter(this::isNewPdvProductRequest)
            .count();
    }

    private boolean isExtensionOwnedByCommercial(
        dossier_affiliation dossier,
        commerciale authenticatedCommerciale
    ) {
        if (dossier == null || authenticatedCommerciale == null || authenticatedCommerciale.getIdCommercial() == null) {
            return false;
        }

        Long authenticatedCommercialeId = authenticatedCommerciale.getIdCommercial();
        commerciale extensionCommerciale = dossier.getCommerciale();
        if (
            extensionCommerciale != null
                && Objects.equals(extensionCommerciale.getIdCommercial(), authenticatedCommercialeId)
        ) {
            return true;
        }

        return findAcceptedPrincipalDossier(dossier.getCommercant())
            .map(dossier_affiliation::getCommerciale)
            .map(commerciale::getIdCommercial)
            .map(principalCommercialeId -> Objects.equals(principalCommercialeId, authenticatedCommercialeId))
            .orElse(false);
    }

    private boolean isExtensionOwnedByBackOffice(
        dossier_affiliation dossier,
        back_office authenticatedBackOffice
    ) {
        if (dossier == null || authenticatedBackOffice == null || authenticatedBackOffice.getIdBackOffice() == null) {
            return false;
        }

        Long authenticatedBackOfficeId = authenticatedBackOffice.getIdBackOffice();
        back_office extensionBackOffice = dossier.getBackOffice();
        if (
            extensionBackOffice != null
                && Objects.equals(extensionBackOffice.getIdBackOffice(), authenticatedBackOfficeId)
        ) {
            return true;
        }

        return findAcceptedPrincipalDossier(dossier.getCommercant())
            .map(dossier_affiliation::getBackOffice)
            .map(back_office::getIdBackOffice)
            .map(principalBackOfficeId -> Objects.equals(principalBackOfficeId, authenticatedBackOfficeId))
            .orElse(false);
    }

    private boolean isBackOfficeVisibleRequest(
        dossier_affiliation dossier,
        back_office authenticatedBackOffice
    ) {
        if (dossier == null || authenticatedBackOffice == null) {
            return false;
        }
        if (isNewPdvProductRequest(dossier) && !isExtensionOwnedByBackOffice(dossier, authenticatedBackOffice)) {
            return false;
        }

        StatusDossier status = dossier.getStatus();
        if (status == StatusDossier.EN_ATTENTE_VALIDATION_BOA) {
            return true;
        }
        if (
            status == StatusDossier.CONTRAT_A_SIGNER
                || status == StatusDossier.ACCEPTE
                || status == StatusDossier.ABANDONNE
        ) {
            return isHandledByBackOffice(dossier, authenticatedBackOffice);
        }
        return false;
    }

    private boolean isHandledByBackOffice(
        dossier_affiliation dossier,
        back_office authenticatedBackOffice
    ) {
        if (
            dossier == null
                || authenticatedBackOffice == null
                || dossier.getBackOffice() == null
                || dossier.getBackOffice().getIdBackOffice() == null
                || authenticatedBackOffice.getIdBackOffice() == null
        ) {
            return false;
        }
        return Objects.equals(
            dossier.getBackOffice().getIdBackOffice(),
            authenticatedBackOffice.getIdBackOffice()
        );
    }

    private void validateExtensionBackOfficeOwnership(
        dossier_affiliation dossier,
        back_office authenticatedBackOffice
    ) {
        if (!isNewPdvProductRequest(dossier) || isExtensionOwnedByBackOffice(dossier, authenticatedBackOffice)) {
            return;
        }

        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cette demande d'extension doit etre traitee par le back office du dossier principal."
        );
    }

    /**
     * Convertit le type d'affiliation du dossier en type de contrat
     * pour l'entité {@link com.example.demo.entities.contrat}.
     */
    private com.example.demo.enums.TypeContrat resolveTypeContrat(dossier_affiliation dossier) {
        if (dossier.getTypeAffiliation() == null) {
            return null;
        }
        return switch (dossier.getTypeAffiliation()) {
            case TPE        -> TypeContrat.AFFILIATION_TPE;
            case SOFTPOS    -> TypeContrat.SOFT_POS;
            case QR_CODE    -> TypeContrat.QR_CODE;
            case E_COMMERCE -> TypeContrat.E_COMMERCE;
            // Dossier combine : approveDossierForContract cree deux lignes contrat
            // via resolveEncaissementProductType() + E_COMMERCE plutot que d'appeler
            // ce helper mono-valeur pour ce cas.
            case ENCAISSEMENT_ET_ECOMMERCE -> null;
        };
    }

    /**
     * Pour un dossier combine (ENCAISSEMENT_ET_ECOMMERCE), deduit le produit
     * d'encaissement choisi par le commercant a partir des champs deja remplis -
     * aucune colonne dediee, on reutilise les champs existants du dossier.
     */
    private TypeContrat resolveEncaissementProductType(dossier_affiliation dossier) {
        if (StringUtils.hasText(dossier.getModeMiseADispositionTpe())) {
            return TypeContrat.AFFILIATION_TPE;
        }
        String modeleQrSoftpos = safe(dossier.getModeleQrSoftpos()).toUpperCase(Locale.ROOT);
        if (modeleQrSoftpos.contains("QR")) {
            return TypeContrat.QR_CODE;
        }
        return TypeContrat.SOFT_POS;
    }

    private void enregistrerLigneContrat(
        dossier_affiliation dossier,
        ServiceDocumentContratAffiliation.ContratGenere contratGenere,
        TypeContrat typeContrat
    ) {
        contrat nouveauContrat = new contrat();
        nouveauContrat.setDossierAffiliation(dossier);
        nouveauContrat.setPdv(dossier.getRequestedPdv());
        nouveauContrat.setTypeContrat(typeContrat);
        nouveauContrat.setStatutContrat(StatusContrat.GENERE);
        nouveauContrat.setDateGeneration(contratGenere.dateGeneration());
        nouveauContrat.setGeneratedContractPath(contratGenere.cheminStocke());
        nouveauContrat.setGeneratedContractFileName(contratGenere.nomFichier());
        nouveauContrat.setGeneratedContractAt(contratGenere.dateGeneration());
        nouveauContrat.setCommercialReportPath(dossier.getCommercialReportPath());
        nouveauContrat.setCommercialReportFileName(dossier.getCommercialReportFileName());
        nouveauContrat.setCommercialReportGeneratedAt(dossier.getCommercialReportGeneratedAt());
        contratRepository.save(nouveauContrat);
    }

    private void provisionRequestedTerminals(dossier_affiliation dossier, pdv acceptedPdv) {
        if (dossier == null || acceptedPdv == null || acceptedPdv.getIdPDV() == null) {
            return;
        }

        TypeAffiliation typeAffiliation = dossier.getTypeAffiliation();
        int requestedCount = resolveRequestedTerminalCount(dossier);
        if (typeAffiliation == null || requestedCount <= 0) {
            return;
        }

        long existingCount = tpeRepository.countByPdv_IdPDV(acceptedPdv.getIdPDV());
        int missingCount = requestedCount - (int) existingCount;
        if (missingCount <= 0) {
            return;
        }

        for (int index = 1; index <= missingCount; index++) {
            tpe terminal = new tpe();
            terminal.setNumeroSerie(generateTerminalSerial(dossier, acceptedPdv, index));
            terminal.setModele(resolveTerminalModel(dossier));
            terminal.setTypeCompatible(typeAffiliation.name());
            terminal.setTypeConnexion(resolveTerminalConnection(dossier));
            terminal.setStatut("AFFECTE_COMMERCANT");
            terminal.setActif(Boolean.TRUE);
            terminal.setDateActivation(LocalDate.now());
            terminal.setDateAffectationCommerciale(LocalDate.now());
            terminal.setPdv(acceptedPdv);
            terminal.setCommerciale(dossier.getCommerciale());
            tpeRepository.save(terminal);
        }
    }

    private int resolveRequestedTerminalCount(dossier_affiliation dossier) {
        TypeAffiliation typeAffiliation = dossier.getTypeAffiliation();
        if (typeAffiliation == null || typeAffiliation == TypeAffiliation.E_COMMERCE) {
            return 0;
        }
        if (typeAffiliation == TypeAffiliation.TPE) {
            Integer nombreTpe = dossier.getNombreTpe();
            return nombreTpe == null || nombreTpe < 1 ? 1 : nombreTpe;
        }
        return 1;
    }

    private String resolveTerminalModel(dossier_affiliation dossier) {
        TypeAffiliation typeAffiliation = dossier.getTypeAffiliation();
        if (typeAffiliation == TypeAffiliation.TPE) {
            return firstNotBlank(dossier.getEquipementTpe(), "TPE STANDARD");
        }
        if (typeAffiliation == TypeAffiliation.SOFTPOS || typeAffiliation == TypeAffiliation.QR_CODE) {
            return firstNotBlank(dossier.getModeleQrSoftpos(), typeAffiliation.name());
        }
        return typeAffiliation == null ? "TERMINAL" : typeAffiliation.name();
    }

    private String resolveTerminalConnection(dossier_affiliation dossier) {
        return firstNotBlank(
            dossier.getConnectiviteTpe(),
            dossier.getModeleQrSoftpos(),
            dossier.getModeServiceEcommerce(),
            "STANDARD"
        );
    }

    private String generateTerminalSerial(dossier_affiliation dossier, pdv acceptedPdv, int index) {
        String prefix = firstNotBlank(
            dossier.getTypeAffiliation() == null ? "" : dossier.getTypeAffiliation().name(),
            "TPE"
        ).replace("_", "");
        String base = "%s-PDV%s-D%s-%02d".formatted(
            prefix,
            acceptedPdv.getIdPDV(),
            dossier.getIdDossier(),
            index
        );
        String serial = base;
        int attempt = 1;
        while (tpeRepository.existsByNumeroSerie(serial)) {
            attempt++;
            serial = base + "-" + attempt;
        }
        return serial;
    }

    private String buildNewPdvContractAvailableEmailBody(
        dossier_affiliation dossier,
        String merchantName
    ) {
        String pointVente = dossier.getRequestedPdv() == null
            ? "votre nouveau point de vente"
            : firstNotBlank(
                dossier.getRequestedPdv().getNomPDV(),
                dossier.getRequestedPdv().getVille(),
                "votre nouveau point de vente"
            );

        return """
            Bonjour %s,

            Votre demande de nouveau point de vente #%s a été traitée par l'équipe commerciale.
            Le contrat et le compte-rendu commercial liés à %s sont maintenant disponibles dans votre espace commerçant.

            Merci de vous connecter à votre espace Lana Cash, consulter le contrat, le signer puis le téléverser.
            Après le dépôt du contrat signé, la demande sera transmise automatiquement au back office pour vérification finale.

            Cordialement,
            L'équipe Lana Cash
            """.formatted(
                firstNotBlank(merchantName, "Commerçant"),
                dossier.getIdDossier(),
                pointVente
            );
    }

    private void validateAutoAffiliationAssignment(
        dossier_affiliation dossier,
        commerciale authenticatedCommerciale
    ) {
        if (
            isCommercialDirectDossier(dossier)
                || (
                    isNewPdvProductRequest(dossier)
                        ? isExtensionOwnedByCommercial(dossier, authenticatedCommerciale)
                        : isAssignedToCommercial(dossier, authenticatedCommerciale)
                )
        ) {
            return;
        }

        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Cette demande d'auto-affiliation ne vous a pas ete assignee par le superviseur."
        );
    }

    private boolean isAssignedToCommercial(
        dossier_affiliation dossier,
        commerciale authenticatedCommerciale
    ) {
        return dossier != null
            && authenticatedCommerciale != null
            && dossier.getCommercialeAssignee() != null
            && authenticatedCommerciale.getIdCommercial().equals(
                dossier.getCommercialeAssignee().getIdCommercial()
            );
    }

    private void validateCommercialDirectOwnership(
        dossier_affiliation dossier,
        commerciale authenticatedCommerciale
    ) {
        if (!isCommercialDirectDossier(dossier)) {
            return;
        }
        if (
            dossier.getCommerciale() == null
                || authenticatedCommerciale.getIdCommercial() == null
                || !Objects.equals(
                    dossier.getCommerciale().getIdCommercial(),
                    authenticatedCommerciale.getIdCommercial()
                )
        ) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Ce dossier appartient a une autre commerciale."
            );
        }
    }

    private void validateCommercialInteractionEligibility(dossier_affiliation dossier) {
        if (
            dossier.getStatus() == StatusDossier.BROUILLON
                || dossier.getStatus() == StatusDossier.SOUMIS
                || dossier.getStatus() == StatusDossier.INCOMPLET
        ) {
            return;
        }

        throw new IllegalArgumentException(
            "Cette prospection est déjà finalisée. Les interactions ne peuvent plus être ajoutées."
        );
    }

    private utilisateur readAuthenticatedStaff(
        String authorizationHeader,
        RoleUser... allowedRoles
    ) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentification Keycloak requise."
                )
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

        RoleUser role = utilisateur.getRole();
        if (
            role != RoleUser.SUPERVISEUR
                && role != RoleUser.COMMERCIAL
                && role != RoleUser.BACK_OFFICE
        ) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Seuls les superviseurs, commerciaux et back office peuvent gerer les dossiers."
            );
        }

        if (allowedRoles != null && allowedRoles.length > 0) {
            for (RoleUser allowedRole : allowedRoles) {
                if (role == allowedRole) {
                    return utilisateur;
                }
            }

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Vous n'avez pas les droits necessaires pour cette action."
            );
        }

        return utilisateur;
    }

    private boolean isAccountActive(dossier_affiliation dossier) {
        commercant commercant = dossier.getCommercant();
        utilisateur utilisateur = commercant == null ? null : commercant.getUtilisateur();
        return utilisateur != null && Boolean.TRUE.equals(utilisateur.getActive());
    }

    private dossier_affiliation readDossier(Long dossierId) {
        return dossierAffiliationRepository
            .findById(dossierId)
            .orElseThrow(() -> new IllegalArgumentException("Dossier d'affiliation introuvable."));
    }

    /**
     * Applique les memes regles de visibilite que {@link #getRequests(String)} pour empecher
     * un commercial ou un back office de consulter/telecharger un dossier hors de son perimetre.
     */
    private void validateStaffCanAccessDossier(utilisateur authenticatedUser, dossier_affiliation dossier) {
        if (authenticatedUser.getRole() == RoleUser.SUPERVISEUR) {
            return;
        }

        if (authenticatedUser.getRole() == RoleUser.BACK_OFFICE) {
            back_office authenticatedBackOffice = backOfficeRepository
                .findByUtilisateur_Id(authenticatedUser.getId())
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte back office introuvable.")
                );
            if (!isBackOfficeVisibleRequest(dossier, authenticatedBackOffice)) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Ce dossier n'est pas rattache a votre perimetre back office."
                );
            }
            return;
        }

        if (authenticatedUser.getRole() == RoleUser.COMMERCIAL) {
            commerciale authenticatedCommerciale = commercialeRepository
                .findByUtilisateur_Id(authenticatedUser.getId())
                .orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte commercial introuvable.")
                );

            boolean allowed = isNewPdvProductRequest(dossier)
                ? isExtensionOwnedByCommercial(dossier, authenticatedCommerciale)
                : isCommercialDirectDossier(dossier)
                    ? dossier.getCommerciale() != null
                        && Objects.equals(
                            dossier.getCommerciale().getIdCommercial(),
                            authenticatedCommerciale.getIdCommercial()
                        )
                    : isAssignedToCommercial(dossier, authenticatedCommerciale);

            if (!allowed) {
                throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Ce dossier n'est pas rattache a votre perimetre commercial."
                );
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role non autorise pour cette action.");
    }

    private void validateCompletionEligibility(dossier_affiliation dossier) {
        if (isAccountActive(dossier) && !isNewPdvProductRequest(dossier)) {
            throw new IllegalArgumentException("Le dossier est déjà actif.");
        }

        if (
            dossier.getStatus() != StatusDossier.SOUMIS
                && dossier.getStatus() != StatusDossier.BROUILLON
                && dossier.getStatus() != StatusDossier.INCOMPLET
        ) {
            throw new IllegalArgumentException(
                "Seul un dossier soumis, brouillon ou incomplet peut etre complète par la commerciale."
            );
        }
    }

    private void validateReviewEligibility(dossier_affiliation dossier) {
        if (dossier.getStatus() != StatusDossier.EN_ATTENTE_VALIDATION_BOA) {
            throw new IllegalArgumentException(
                "Le back office ne peut valider qu'un dossier déjà complète par la commerciale."
            );
        }
    }

    private int resolveTokenVersion(utilisateur utilisateur) {
        return utilisateur.getTokenVersion() == null ? 0 : utilisateur.getTokenVersion();
    }

    private void clearPendingAuthentication(utilisateur utilisateur) {
        utilisateur.setLoginOtpChallengeId(null);
        utilisateur.setLoginOtpCodeHash(null);
        utilisateur.setLoginOtpExpiresAt(null);
        utilisateur.setLoginOtpFailedAttempts(0);
        utilisateur.setPasswordResetCodeHash(null);
        utilisateur.setPasswordResetExpiresAt(null);
        utilisateur.setPasswordResetFailedAttempts(0);
    }

    private String buildMerchantEmailBody(
        dossier_affiliation dossier,
        boolean accepted,
        String motifRefus,
        String merchantName
    ) {
        String recipientName = safe(firstNotBlank(merchantName, "Madame, Monsieur"));
        StringBuilder body = new StringBuilder()
            .append("Bonjour ")
            .append(recipientName)
            .append(",\n\n")
            .append("Nous vous informons que votre dossier d'affiliation #")
            .append(dossier.getIdDossier())
            .append(
                accepted
                    ? " a été validé par le back office Lana Cash.\n"
                    : " a été refusé par le back office Lana Cash.\n"
            );

        if (accepted) {
            body.append("\nVotre affiliation est maintenant validée.\n");
        } else {
            body
                .append("\nMotif du refus : ")
                .append(safe(motifRefus))
                .append("\n");
        }

        body.append("\nCordialement,\nL'équipe Lana Cash");
        return body.toString();
    }

    private String buildCommercialEmailBody(
        dossier_affiliation dossier,
        boolean accepted,
        String motifRefus,
        String merchantName,
        String commercialDisplayName
    ) {
        String recipientName = safe(firstNotBlank(commercialDisplayName, "Madame, Monsieur"));
        StringBuilder body = new StringBuilder()
            .append("Bonjour ")
            .append(recipientName)
            .append(",\n\n")
            .append("Nous vous informons que le dossier d'affiliation #")
            .append(dossier.getIdDossier())
            .append(" du commerçant ")
            .append(merchantName)
            .append(accepted ? " a été validé" : " a été refusé")
            .append(" par le back office Lana Cash.\n");

        if (!accepted) {
            body
                .append("\nMotif du refus : ")
                .append(safe(motifRefus))
                .append("\n");
        }

        body.append("\nCordialement,\nL'équipe Lana Cash");
        return body.toString();
    }

    private String resolveCommercialDisplayName(commerciale commerciale) {
        if (commerciale == null) {
            return "";
        }

        return safe(
            firstNotBlank(
                String.join(
                    " ",
                    Objects.requireNonNullElse(commerciale.getPrenom(), ""),
                    Objects.requireNonNullElse(commerciale.getNom(), "")
                ).trim(),
                commerciale.getUtilisateur() == null ? "" : commerciale.getUtilisateur().getEmail()
            )
        );
    }

    private String resolveBackOfficeDisplayName(back_office backOffice) {
        if (backOffice == null) {
            return "";
        }

        return safe(
            firstNotBlank(
                String.join(
                    " ",
                    Objects.requireNonNullElse(backOffice.getPrenom(), ""),
                    Objects.requireNonNullElse(backOffice.getNom(), "")
                ).trim(),
                backOffice.getUtilisateur() == null ? "" : backOffice.getUtilisateur().getEmail()
            )
        );
    }

    private String resolveMerchantDisplayName(commercant commercant, utilisateur utilisateur) {
        if (commercant == null && utilisateur == null) {
            return "Commerçant";
        }

        return safe(
            firstNotBlank(
                commercant == null ? "" : commercant.getNomCommercial(),
                commercant == null ? "" : commercant.getRaisonSociale(),
                utilisateur == null ? "" : utilisateur.getEmail()
            )
        );
    }

    private String resolveDocumentFileName(String rawPath) {
        return serviceDocumentContratAffiliation.resolveFileName(rawPath);
    }

    private boolean isDownloadableDocument(documents document) {
        return isExistingFile(resolveUploadedDocumentPath(document.getCheminStockage()));
    }

    private Path resolveUploadedDocumentPath(String storedPath) {
        Path directPath = normalizePath(storedPath);
        if (isExistingFile(directPath)) {
            return directPath;
        }

        Path relocatedPath = resolvePathWithinRoot(storedPath, uploadRoot);
        return isExistingFile(relocatedPath) ? relocatedPath : null;
    }

    private Path resolvePathWithinRoot(String rawPath, Path root) {
        String normalizedValue = Objects.requireNonNullElse(rawPath, "").trim().replace('\\', '/');
        if (!StringUtils.hasText(normalizedValue)) {
            return null;
        }

        String[] segments = normalizedValue.split("/");
        int relativeStartIndex = -1;
        for (int index = 0; index < segments.length; index++) {
            if (segments[index].startsWith("dossier-")) {
                relativeStartIndex = index;
                break;
            }
        }

        if (relativeStartIndex >= 0) {
            Path candidate = root;
            for (int index = relativeStartIndex; index < segments.length; index++) {
                if (StringUtils.hasText(segments[index])) {
                    candidate = candidate.resolve(segments[index]);
                }
            }
            return candidate.normalize();
        }

        String fileName = resolveDocumentFileName(normalizedValue);
        return StringUtils.hasText(fileName) ? root.resolve(fileName).normalize() : null;
    }

    private Path normalizePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }

        try {
            return Path.of(rawPath).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isExistingFile(Path path) {
        return path != null && Files.exists(path) && !Files.isDirectory(path);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String resolveCompteRenduOrigineProspect(
        dossier_affiliation dossier,
        AffiliationActivationRequest request
    ) {
        // Dossiers non-commercial-direct (auto-affiliation, NOUVEAU_PDV, ou NULL hérité)
        // → l'API retourne déjà "AUTO_AFFILIATION" en fallback (ligne firstNotBlank),
        //   on doit aligner le backend sur la même logique.
        if (!"COMMERCIAL_DIRECT".equalsIgnoreCase(normalize(dossier.getOrigineCreation()))) {
            return "AUTO_AFFILIATION";
        }

        String origineProspect = normalize(request.compteRenduOrigineProspect());
        if (StringUtils.hasText(origineProspect)) {
            return origineProspect;
        }

        return normalize(dossier.getCompteRenduOrigineProspect());
    }

    private CommercialInteractionResponse.CommercialInteractionItem mapCommercialInteraction(
        interaction_commerciale interaction
    ) {
        commerciale commercial = interaction.getCommerciale();
        utilisateur user = commercial == null ? null : commercial.getUtilisateur();
        String commercialName = commercial == null
            ? ""
            : firstNotBlank(
                String.join(" ", safe(commercial.getPrenom()), safe(commercial.getNom())).trim(),
                user == null ? "" : user.getEmail()
            );

        return new CommercialInteractionResponse.CommercialInteractionItem(
            interaction.getIdInteraction(),
            interaction.getTypeInteraction() == null ? "" : interaction.getTypeInteraction().name(),
            safe(interaction.getResultat()),
            safe(interaction.getCommentaire()),
            safe(interaction.getStatut()),
            interaction.getDateInteraction(),
            interaction.getProchaineRelanceDate(),
            interaction.getProchaineRelanceType() == null
                ? ""
                : interaction.getProchaineRelanceType().name(),
            safe(interaction.getProspectStatus()),
            commercialName
        );
    }

    private TypeInteraction parseInteractionType(String rawValue, String message) {
        String normalizedValue = normalize(rawValue);
        if (!StringUtils.hasText(normalizedValue)) {
            throw new IllegalArgumentException(message);
        }

        try {
            return TypeInteraction.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Type d'interaction commerciale invalide.");
        }
    }

    private TypeInteraction parseOptionalInteractionType(String rawValue) {
        String normalizedValue = normalize(rawValue);
        if (!StringUtils.hasText(normalizedValue)) {
            return null;
        }

        return parseInteractionType(normalizedValue, "Le type de relance est invalide.");
    }

    private LocalDate parseOptionalDate(String rawValue, LocalDate fallbackValue) {
        String normalizedValue = normalize(rawValue);
        if (!StringUtils.hasText(normalizedValue)) {
            return fallbackValue;
        }

        try {
            return LocalDate.parse(normalizedValue);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Format de date invalide.");
        }
    }

    private void validateCommercialInteractionBusinessRules(
        TypeInteraction interactionType,
        TypeInteraction nextInteractionType,
        LocalDate interactionDate,
        LocalDate nextReminderDate,
        ProspectStatus prospectStatus,
        String result,
        String comment,
        String status
    ) {
        if (interactionDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date de l'interaction ne peut pas être dans le futur.");
        }

        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("Le résultat de l'interaction est obligatoire.");
        }

        if (!List.of("FAIT", "PLANIFIE", "ANNULE").contains(status.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Le statut de l'action doit être FAIT, PLANIFIE ou ANNULE.");
        }

        if (nextInteractionType != null && nextReminderDate == null) {
            throw new IllegalArgumentException("La date de prochaine relance est obligatoire si un type de relance est choisi.");
        }

        if (nextReminderDate != null && nextInteractionType == null) {
            throw new IllegalArgumentException("Le type de prochaine relance est obligatoire si une date de relance est choisie.");
        }

        if (nextReminderDate != null && nextReminderDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La date de prochaine relance ne peut pas être dans le passé.");
        }

        if (prospectStatus == ProspectStatus.A_RELANCER && nextReminderDate == null) {
            throw new IllegalArgumentException("Une date de prochaine relance est obligatoire pour le statut À relancer.");
        }

        if (prospectStatus == ProspectStatus.ABANDONNE && !StringUtils.hasText(comment)) {
            throw new IllegalArgumentException("Le motif est obligatoire quand le prospect est abandonné.");
        }

        if (prospectStatus == ProspectStatus.CONVERTI && nextReminderDate != null) {
            throw new IllegalArgumentException("Un prospect converti ne doit pas avoir de prochaine relance.");
        }

        if (interactionType == TypeInteraction.RELANCE && nextReminderDate != null && nextReminderDate.equals(interactionDate)) {
            throw new IllegalArgumentException("La prochaine relance doit être postérieure à la relance effectuée.");
        }
    }

    private ProspectStatus resolveNextProspectStatus(
        dossier_affiliation dossier,
        TypeInteraction interactionType,
        String requestedStatus,
        LocalDate nextReminderDate
    ) {
        String normalizedStatus = normalize(requestedStatus);
        if (StringUtils.hasText(normalizedStatus)) {
            ProspectStatus requestedProspectStatus;
            try {
                requestedProspectStatus = ProspectStatus.valueOf(
                    normalizedStatus.toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Statut de prospection invalide.");
            }

            validateProspectStatusCompatibility(interactionType, requestedProspectStatus);
            return requestedProspectStatus;
        }

        if (interactionType == TypeInteraction.RELANCE) {
            return ProspectStatus.A_RELANCER;
        }

        return switch (interactionType) {
            case RDV -> ProspectStatus.RDV_PLANIFIE;
            case VISITE -> nextReminderDate == null
                ? ProspectStatus.EN_NEGOCIATION
                : ProspectStatus.A_RELANCER;
            case APPEL, EMAIL -> ProspectStatus.CONTACTE;
            case RELANCE -> ProspectStatus.A_RELANCER;
        };
    }

    private void validateProspectStatusCompatibility(
        TypeInteraction interactionType,
        ProspectStatus prospectStatus
    ) {
        boolean compatible = switch (interactionType) {
            case APPEL, EMAIL -> List.of(
                ProspectStatus.CONTACTE,
                ProspectStatus.EN_NEGOCIATION,
                ProspectStatus.A_RELANCER,
                ProspectStatus.ABANDONNE
            ).contains(prospectStatus);
            case RDV -> List.of(
                ProspectStatus.RDV_PLANIFIE,
                ProspectStatus.EN_NEGOCIATION,
                ProspectStatus.A_RELANCER,
                ProspectStatus.ABANDONNE
            ).contains(prospectStatus);
            case VISITE -> List.of(
                ProspectStatus.EN_NEGOCIATION,
                ProspectStatus.A_RELANCER,
                ProspectStatus.ABANDONNE
            ).contains(prospectStatus);
            case RELANCE -> List.of(
                ProspectStatus.A_RELANCER,
                ProspectStatus.CONTACTE,
                ProspectStatus.EN_NEGOCIATION,
                ProspectStatus.ABANDONNE
            ).contains(prospectStatus);
        };

        if (!compatible) {
            throw new IllegalArgumentException(
                "Le statut de prospection ne correspond pas au type d'interaction choisi."
            );
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmedValue = value.trim();
        return switch (trimmedValue.toLowerCase(Locale.ROOT)) {
            case "null", "undefined" -> null;
            default -> trimmedValue;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    private String sanitizeFileName(String fileName) {
        String normalized = normalize(fileName);
        if (!StringUtils.hasText(normalized)) {
            return "document";
        }
        return normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }

    private record MerchantProfileSnapshot(
        String nom,
        String prenom,
        String cin,
        String raisonSociale,
        String rc,
        String ice,
        String formeJuridique,
        String representantLegal,
        String numeroAutoEntrepreneur,
        String nomEntite,
        String objet
    ) {
        private static MerchantProfileSnapshot empty() {
            return new MerchantProfileSnapshot("", "", "", "", "", "", "", "", "", "", "");
        }
    }

    private record PointVenteRequest(
        String nom,
        String adresse,
        String ville,
        String codePostal,
        String quartier,
        String telephone,
        String email
    ) {
    }

    private record DocumentDefinition(
        String key,
        TypeDocument typeDocument
    ) {
    }

    public record DocumentDownload(String fileName, String contentType, byte[] content) {
    }

    private DocumentDownload toDocumentDownload(
        ServiceDocumentContratAffiliation.ContratTelecharge contractDownload
    ) {
        return new DocumentDownload(
            contractDownload.nomFichier(),
            contractDownload.typeContenu(),
            contractDownload.contenu()
        );
    }
}
