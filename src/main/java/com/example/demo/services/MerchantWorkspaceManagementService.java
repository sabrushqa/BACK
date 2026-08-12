package com.example.demo.services;

import com.example.demo.dto.MerchantSubMerchantCreateRequest;
import com.example.demo.dto.MerchantSubMerchantCreateResponse;
import com.example.demo.dto.MerchantSubMerchantMoveRequest;
import com.example.demo.dto.MerchantSubMerchantMoveResponse;
import com.example.demo.dto.MerchantSubMerchantStatusResponse;
import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.dto.MerchantTpePdvAssignmentRequest;
import com.example.demo.dto.MerchantTpePdvAssignmentResponse;
import com.example.demo.dto.SupervisorActionResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MerchantWorkspaceManagementService {
    private static final int MAX_TPE = 10;

    private final UtilisateurRepository utilisateurRepository;
    private final CommercantRepository commercantRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final PdvRepository pdvRepository;
    private final SousCommercantRepository sousCommercantRepository;
    private final TpeRepository tpeRepository;
    private final SwitchMonetiqueClient switchMonetiqueClient;
    private final PasswordHashService passwordHashService;
    private final ActivationMailService activationMailService;
    private final JwtService jwtService;
    private final KeycloakAdminService keycloakAdminService;
    private final GeocodingService geocodingService;
    private final long activationExpirationMinutes;

    public MerchantWorkspaceManagementService(
        UtilisateurRepository utilisateurRepository,
        CommercantRepository commercantRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        PdvRepository pdvRepository,
        SousCommercantRepository sousCommercantRepository,
        TpeRepository tpeRepository,
        SwitchMonetiqueClient switchMonetiqueClient,
        PasswordHashService passwordHashService,
        ActivationMailService activationMailService,
        JwtService jwtService,
        KeycloakAdminService keycloakAdminService,
        GeocodingService geocodingService,
        @Value("${app.auth.activation-expiration-minutes:60}") long activationExpirationMinutes
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercantRepository = commercantRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.pdvRepository = pdvRepository;
        this.sousCommercantRepository = sousCommercantRepository;
        this.tpeRepository = tpeRepository;
        this.switchMonetiqueClient = switchMonetiqueClient;
        this.passwordHashService = passwordHashService;
        this.activationMailService = activationMailService;
        this.jwtService = jwtService;
        this.keycloakAdminService = keycloakAdminService;
        this.geocodingService = geocodingService;
        this.activationExpirationMinutes = activationExpirationMinutes;
    }

    public MerchantSubMerchantCreateResponse createSubMerchant(
        String authorizationHeader,
        MerchantSubMerchantCreateRequest request
    ) {
        utilisateur merchantUser = readAuthenticatedUser(authorizationHeader);
        if (merchantUser.getRole() != RoleUser.COMMERCANT) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Seul un commerçant peut créer un sous-commerçant."
            );
        }

        commercant commercant = commercantRepository
            .findByUtilisateur_Id(merchantUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commerçant introuvable."));

        if (request == null) {
            throw new IllegalArgumentException("Le point de vente est obligatoire.");
        }

        boolean isEcommerce = resolveWorkspaceTypeAffiliation(commercant) == TypeAffiliation.E_COMMERCE;

        pdv pointVente = null;
        String canalEcommerce = null;
        if (isEcommerce) {
            canalEcommerce = normalize(request.canalEcommerce());
            if (!"SITE_MARCHAND".equals(canalEcommerce) && !"APPLICATION_MOBILE".equals(canalEcommerce)) {
                throw new IllegalArgumentException(
                    "Le canal (site marchand ou application mobile) est obligatoire."
                );
            }
        } else {
            if (request.pdvId() == null) {
                throw new IllegalArgumentException("Le point de vente est obligatoire.");
            }

            pointVente = pdvRepository
                .findById(request.pdvId())
                .filter((pdv) -> pdv.getCommercant() != null
                    && Objects.equals(pdv.getCommercant().getIdCommercant(), commercant.getIdCommercant()))
                .orElseThrow(
                    () -> new IllegalArgumentException("Le point de vente sélectionné est introuvable.")
                );

            if (pointVente.getSousCommercant() != null) {
                throw new IllegalArgumentException("Ce point de vente est déjà affecté à un sous-commerçant.");
            }
            validateAssignablePdv(pointVente);
        }

        requireText(request.nom(), "Le nom du sous-commerçant est obligatoire.");
        requireText(request.prenom(), "Le prénom du sous-commerçant est obligatoire.");
        requireText(request.email(), "L'e-mail du sous-commerçant est obligatoire.");

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (email.equalsIgnoreCase(merchantUser.getEmail())) {
            throw new IllegalArgumentException(
                "L'e-mail du sous-commerçant doit être différent de l'e-mail du commerçant."
            );
        }

        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet e-mail.");
        }

        String temporaryPassword = generateAccountSecret();
        utilisateur subMerchantUser = new utilisateur();
        subMerchantUser.setEmail(email);
        subMerchantUser.setPassword(null);
        subMerchantUser.setRole(RoleUser.SOUS_COMMERCANT);
        subMerchantUser.setActive(Boolean.FALSE);
        subMerchantUser.setDateActivation(null);
        subMerchantUser.setPasswordExpiresAt(LocalDateTime.now().plusMinutes(activationExpirationMinutes));
        subMerchantUser.setTokenVersion(0);
        subMerchantUser = utilisateurRepository.save(subMerchantUser);
        if (!keycloakAdminService.provisionUser(subMerchantUser, temporaryPassword)) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le compte Keycloak n'a pas pu être préparé. Aucun e-mail d'activation n'a été envoyé."
            );
        }
        subMerchantUser = utilisateurRepository.save(subMerchantUser);

        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setNom(normalize(request.nom()));
        sousCommercant.setPrenom(normalize(request.prenom()));
        sousCommercant.setEmail(email);
        sousCommercant.setTelephone(normalize(request.telephone()));
        sousCommercant.setDateAffectation(LocalDate.now());
        sousCommercant.setStatut("EN_ATTENTE");
        sousCommercant.setUtilisateur(subMerchantUser);
        if (isEcommerce) {
            sousCommercant.setCommercant(commercant);
            sousCommercant.setCanalEcommerce(canalEcommerce);
        }
        sousCommercant = sousCommercantRepository.save(sousCommercant);

        if (!isEcommerce) {
            pointVente.setSousCommercant(sousCommercant);
            pdvRepository.save(pointVente);
        }

        ActivationMailService.MailDispatchResult mailResult = activationMailService.sendAccountSetupEmail(
            subMerchantUser,
            (normalize(request.prenom()) + " " + normalize(request.nom())).trim(),
            "sous-commerçant",
            temporaryPassword
        );

        return new MerchantSubMerchantCreateResponse(
            sousCommercant.getIdSousCommercant(),
            isEcommerce
                ? "Le sous-commerçant a été créé et rattaché au canal sélectionné."
                : "Le sous-commerçant a été créé et affecté au point de vente.",
            mailResult.sent(),
            mailResult.message()
        );
    }

    private TypeAffiliation resolveWorkspaceTypeAffiliation(commercant commercant) {
        List<dossier_affiliation> merchantDossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
                commercant.getIdCommercant()
            );
        dossier_affiliation latestDossier = merchantDossiers.stream().findFirst().orElse(null);
        dossier_affiliation workspaceDossier = merchantDossiers.stream()
            .filter(dossier -> !"NOUVEAU_PDV".equalsIgnoreCase(
                (dossier.getOrigineCreation() == null ? "" : dossier.getOrigineCreation()).trim()
            ))
            .findFirst()
            .orElse(latestDossier);
        return workspaceDossier == null ? null : workspaceDossier.getTypeAffiliation();
    }

    public SupervisorActionResponse requestNewPdvProduct(
        String authorizationHeader,
        MerchantPdvProductRequest request
    ) {
        utilisateur merchantUser = readAuthenticatedUser(authorizationHeader);
        if (merchantUser.getRole() != RoleUser.COMMERCANT) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Seul un commerçant peut demander un nouveau point de vente."
            );
        }
        if (!Boolean.TRUE.equals(merchantUser.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le compte commerçant doit etre actif.");
        }

        commercant commercant = commercantRepository
            .findByUtilisateur_Id(merchantUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commerçant introuvable."));

        if (request == null) {
            throw new IllegalArgumentException("Les informations de la demande sont obligatoires.");
        }

        TypeAffiliation typeAffiliation = parseTypeAffiliation(request.typeAffiliation());
        dossier_affiliation principalDossier = resolveAcceptedWorkspaceDossier(commercant);
        TypeAffiliation currentAffiliationType = principalDossier.getTypeAffiliation();
        if (!isCompatibleAugmentationType(currentAffiliationType, typeAffiliation)) {
            throw new IllegalArgumentException(
                "Cette demande doit rester compatible avec votre affiliation actuelle: "
                    + resolveAffiliationFamilyLabel(currentAffiliationType)
                    + "."
            );
        }

        boolean isEcommerce = typeAffiliation == TypeAffiliation.E_COMMERCE;

        // E-commerce merchants have no physical point de vente — they request a
        // new site marchand / application mobile channel instead, so no pdv row
        // is created and the dossier carries no requestedPdv.
        pdv pointVente = null;
        if (isEcommerce) {
            requireText(request.modeServiceEcommerce(), "Le mode de service e-commerce est obligatoire.");
            if (!StringUtils.hasText(request.siteMarchandUrl()) && !StringUtils.hasText(request.applicationMobile())) {
                throw new IllegalArgumentException("Le site marchand ou l'application mobile est obligatoire.");
            }
        } else {
            requireText(request.nom(), "Le nom du point de vente est obligatoire.");
            requireText(request.adresse(), "L'adresse du point de vente est obligatoire.");
            requireText(request.ville(), "La ville du point de vente est obligatoire.");
            requireText(request.telephone(), "Le téléphone du point de vente est obligatoire.");

            pointVente = new pdv();
            pointVente.setNomPDV(normalize(request.nom()));
            pointVente.setAdresse(normalize(request.adresse()));
            pointVente.setVille(normalize(request.ville()));
            pointVente.setCodePostal(normalize(request.codePostal()));
            pointVente.setQuartier(normalize(request.quartier()));
            pointVente.setTelephone(normalize(request.telephone()));
            pointVente.setEmail(normalize(request.email()));
            pointVente.setStatut("EN_VERIFICATION");
            pointVente.setDateCreation(LocalDate.now());
            pointVente.setCommercant(commercant);
            if (request.latitude() != null && request.longitude() != null) {
                // Position pointee manuellement par le commercant sur la mini-carte du
                // formulaire - plus fiable qu'un geocodage texte, on ne retente pas
                // Nominatim dans ce cas.
                pointVente.setLatitude(request.latitude());
                pointVente.setLongitude(request.longitude());
            } else {
                Optional<GeocodingService.Coordonnees> coordonnees = geocodingService.geocoder(
                    pointVente.getAdresse(),
                    pointVente.getQuartier(),
                    pointVente.getVille(),
                    pointVente.getCodePostal()
                );
                if (coordonnees.isPresent()) {
                    pointVente.setLatitude(coordonnees.get().latitude());
                    pointVente.setLongitude(coordonnees.get().longitude());
                }
            }
            pdvRepository.save(pointVente);
        }

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(principalDossier.getCommerciale());
        dossier.setBackOffice(principalDossier.getBackOffice());
        dossier.setRequestedPdv(pointVente);
        // Reused for both flows: MerchantAccessService.resolveWorkspaceDossier
        // excludes any "NOUVEAU_PDV" dossier so an extension request (physical
        // PDV or e-commerce channel) never overrides the merchant's reference
        // (approved) dossier when building their session.
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(typeAffiliation);
        dossier.setDateSoumission(LocalDate.now());
        dossier.setModeMiseADispositionTpe(normalize(request.modeMiseADispositionTpe()));
        dossier.setNombreTpe(parseOptionalIntegerInRange(request.nombreTpe(), "Le nombre de TPE", 1, MAX_TPE));
        dossier.setEquipementTpe(normalize(request.equipementTpe()));
        dossier.setConnectiviteTpe(normalize(request.connectiviteTpe()));
        dossier.setModeleQrSoftpos(normalize(request.modeleQrSoftpos()));
        dossier.setModeServiceEcommerce(normalize(request.modeServiceEcommerce()));
        dossier.setSiteMarchandUrl(normalize(request.siteMarchandUrl()));
        dossier.setApplicationMobile(normalize(request.applicationMobile()));
        dossierAffiliationRepository.save(dossier);

        return new SupervisorActionResponse(
            isEcommerce
                ? "Votre demande de nouveau canal e-commerce a été envoyée à l'équipe commerciale."
                : "Votre demande de nouveau point de vente a été envoyée à l'équipe commerciale."
        );
    }

    public MerchantTpePdvAssignmentResponse assignTpeToPdv(
        String authorizationHeader,
        String tpeId,
        MerchantTpePdvAssignmentRequest request
    ) {
        utilisateur merchantUser = readAuthenticatedUser(authorizationHeader);
        if (merchantUser.getRole() != RoleUser.COMMERCANT) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Seul un commerçant peut affecter un TPE à un point de vente."
            );
        }

        if (!StringUtils.hasText(tpeId)) {
            throw new IllegalArgumentException("Le TPE est obligatoire.");
        }
        if (request == null || request.pdvId() == null || request.pdvId() < 1) {
            throw new IllegalArgumentException("Le point de vente est obligatoire.");
        }

        commercant commercant = commercantRepository
            .findByUtilisateur_Id(merchantUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commerçant introuvable."));

        Long commercantId = commercant.getIdCommercant();
        pdv targetPdv = pdvRepository
            .findById(request.pdvId())
            .filter((pdv) -> pdv.getCommercant() != null
                && Objects.equals(pdv.getCommercant().getIdCommercant(), commercantId))
            .orElseThrow(
                () -> new IllegalArgumentException("Le point de vente sélectionné est introuvable.")
            );

        // Deux univers de TPE possibles : la table locale (auto-provisionnes
        // pour NOUVEAU_PDV, id numerique) et Oracle (affectes par le BOA via
        // le flux principal, id texte type "TPE-000123"). On essaie d'abord
        // le local (comportement historique inchange), sinon on bascule sur
        // Oracle apres avoir verifie que le TPE appartient bien a ce commercant.
        Optional<tpe> localTerminal = parseLocalTpeId(tpeId)
            .flatMap(id -> tpeRepository.findAssignedToCommercant(id, commercantId));

        if (localTerminal.isPresent()) {
            tpe terminal = localTerminal.get();
            terminal.setPdv(targetPdv);
            tpeRepository.save(terminal);
            return new MerchantTpePdvAssignmentResponse(
                String.valueOf(terminal.getIdTPE()),
                targetPdv.getIdPDV(),
                "Le TPE a été affecté au point de vente sélectionné."
            );
        }

        SwitchMonetiqueClient.SwitchTpe oracleTerminal = switchMonetiqueClient.parId(tpeId)
            .orElseThrow(() -> new IllegalArgumentException("Le TPE sélectionné n'est pas affecté à votre compte."));
        if (!String.valueOf(commercantId).equals(oracleTerminal.idCommercant())) {
            throw new IllegalArgumentException("Le TPE sélectionné n'est pas affecté à votre compte.");
        }

        switchMonetiqueClient.mettreAJourPdv(tpeId, targetPdv.getIdPDV().toString());

        return new MerchantTpePdvAssignmentResponse(
            tpeId,
            targetPdv.getIdPDV(),
            "Le TPE a été affecté au point de vente sélectionné."
        );
    }

    private Optional<Long> parseLocalTpeId(String tpeId) {
        try {
            return Optional.of(Long.parseLong(tpeId));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public MerchantSubMerchantStatusResponse activateSubMerchant(
        String authorizationHeader,
        Long subMerchantId
    ) {
        sous_commercant sousCommercant = findOwnedSubMerchant(authorizationHeader, subMerchantId);
        utilisateur subUser = sousCommercant.getUtilisateur();
        if (subUser == null) {
            throw new IllegalArgumentException("Le compte utilisateur du sous-commerçant est introuvable.");
        }

        subUser.setActive(Boolean.TRUE);
        subUser.setDateActivation(LocalDate.now());
        subUser.setDateDesactivation(null);
        subUser.setTokenVersion(resolveTokenVersion(subUser) + 1);
        utilisateurRepository.save(subUser);

        sousCommercant.setStatut("ACTIF");
        sousCommercantRepository.save(sousCommercant);

        return new MerchantSubMerchantStatusResponse(
            sousCommercant.getIdSousCommercant(),
            true,
            sousCommercant.getStatut(),
            "Le compte sous-commerçant a été activé."
        );
    }

    public MerchantSubMerchantStatusResponse deactivateSubMerchant(
        String authorizationHeader,
        Long subMerchantId
    ) {
        sous_commercant sousCommercant = findOwnedSubMerchant(authorizationHeader, subMerchantId);
        utilisateur subUser = sousCommercant.getUtilisateur();
        if (subUser == null) {
            throw new IllegalArgumentException("Le compte utilisateur du sous-commerçant est introuvable.");
        }

        subUser.setActive(Boolean.FALSE);
        subUser.setDateDesactivation(LocalDate.now());
        subUser.setTokenVersion(resolveTokenVersion(subUser) + 1);
        utilisateurRepository.save(subUser);

        sousCommercant.setStatut("INACTIF");
        sousCommercantRepository.save(sousCommercant);

        return new MerchantSubMerchantStatusResponse(
            sousCommercant.getIdSousCommercant(),
            false,
            sousCommercant.getStatut(),
            "Le compte sous-commerçant a été désactivé."
        );
    }

    public MerchantSubMerchantMoveResponse moveSubMerchantToPdv(
        String authorizationHeader,
        Long subMerchantId,
        MerchantSubMerchantMoveRequest request
    ) {
        OwnedSubMerchant ownedSubMerchant = resolveOwnedSubMerchant(authorizationHeader, subMerchantId);
        if (request == null || request.pdvId() == null || request.pdvId() < 1) {
            throw new IllegalArgumentException("Le point de vente cible est obligatoire.");
        }

        pdv targetPdv = pdvRepository
            .findById(request.pdvId())
            .filter((pdv) -> pdv.getCommercant() != null
                && Objects.equals(
                    pdv.getCommercant().getIdCommercant(),
                    ownedSubMerchant.commercant().getIdCommercant()
                ))
            .orElseThrow(
                () -> new IllegalArgumentException("Le point de vente sélectionné est introuvable.")
            );

        if (
            targetPdv.getSousCommercant() != null
                && !Objects.equals(
                    targetPdv.getSousCommercant().getIdSousCommercant(),
                    ownedSubMerchant.sousCommercant().getIdSousCommercant()
                )
        ) {
            throw new IllegalArgumentException("Ce point de vente est déjà affecté à un autre sous-commerçant.");
        }
        validateAssignablePdv(targetPdv);

        pdv currentPdv = ownedSubMerchant.pointVente();
        if (currentPdv != null && !Objects.equals(currentPdv.getIdPDV(), targetPdv.getIdPDV())) {
            currentPdv.setSousCommercant(null);
            pdvRepository.save(currentPdv);
        }

        targetPdv.setSousCommercant(ownedSubMerchant.sousCommercant());
        pdvRepository.save(targetPdv);

        return new MerchantSubMerchantMoveResponse(
            ownedSubMerchant.sousCommercant().getIdSousCommercant(),
            targetPdv.getIdPDV(),
            "Le sous-commerçant a été déplacé vers le point de vente sélectionné."
        );
    }

    private utilisateur readAuthenticatedUser(String authorizationHeader) {
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

        return utilisateur;
    }

    private Integer resolveTokenVersion(utilisateur utilisateur) {
        return Objects.requireNonNullElse(utilisateur.getTokenVersion(), 0);
    }

    private sous_commercant findOwnedSubMerchant(String authorizationHeader, Long subMerchantId) {
        return resolveOwnedSubMerchant(authorizationHeader, subMerchantId).sousCommercant();
    }

    private OwnedSubMerchant resolveOwnedSubMerchant(String authorizationHeader, Long subMerchantId) {
        utilisateur merchantUser = readAuthenticatedUser(authorizationHeader);
        if (merchantUser.getRole() != RoleUser.COMMERCANT) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Seul un commerçant peut gérer un sous-commerçant."
            );
        }
        if (subMerchantId == null || subMerchantId < 1) {
            throw new IllegalArgumentException("Le sous-commerçant est obligatoire.");
        }

        commercant commercant = commercantRepository
            .findByUtilisateur_Id(merchantUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commerçant introuvable."));
        sous_commercant sousCommercant = sousCommercantRepository.findById(subMerchantId)
            .orElseThrow(() -> new IllegalArgumentException("Le sous-commerçant sélectionné est introuvable."));

        pdv pointVente = pdvRepository
            .findByCommercant_IdCommercantOrderByIdPDVAsc(commercant.getIdCommercant())
            .stream()
            .filter(pdv -> pdv.getSousCommercant() != null
                && Objects.equals(
                    pdv.getSousCommercant().getIdSousCommercant(),
                    sousCommercant.getIdSousCommercant()
                ))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Ce sous-commerçant n'est pas affecté à votre compte.")
            );

        return new OwnedSubMerchant(commercant, sousCommercant, pointVente);
    }

    private record OwnedSubMerchant(
        commercant commercant,
        sous_commercant sousCommercant,
        pdv pointVente
    ) {
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateAssignablePdv(pdv pointVente) {
        if ("EN_VERIFICATION".equalsIgnoreCase(normalize(pointVente.getStatut()))) {
            throw new IllegalArgumentException(
                "Ce point de vente est encore en vérification et ne peut pas être affecté à un sous-commerçant."
            );
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private TypeAffiliation parseTypeAffiliation(String value) {
        requireText(value, "Le produit demandé est obligatoire.");
        try {
            return TypeAffiliation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Le produit demandé est invalide.");
        }
    }

    private dossier_affiliation resolveAcceptedWorkspaceDossier(commercant commercant) {
        return dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
                commercant.getIdCommercant()
            )
            .stream()
            .filter(dossier -> !isNewPdvProductRequest(dossier))
            .filter(dossier -> dossier.getStatus() == StatusDossier.ACCEPTE)
            .filter(dossier -> dossier.getTypeAffiliation() != null)
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Votre dossier principal doit être accepté avant toute demande d'augmentation."
                )
            );
    }

    private boolean isNewPdvProductRequest(dossier_affiliation dossier) {
        return dossier != null && "NOUVEAU_PDV".equalsIgnoreCase(normalize(dossier.getOrigineCreation()));
    }

    private boolean isCompatibleAugmentationType(
        TypeAffiliation currentAffiliationType,
        TypeAffiliation requestedAffiliationType
    ) {
        if (currentAffiliationType == TypeAffiliation.E_COMMERCE) {
            return requestedAffiliationType == TypeAffiliation.E_COMMERCE;
        }
        return requestedAffiliationType == TypeAffiliation.TPE
            || requestedAffiliationType == TypeAffiliation.SOFTPOS;
    }

    private String resolveAffiliationFamilyLabel(TypeAffiliation typeAffiliation) {
        return typeAffiliation == TypeAffiliation.E_COMMERCE ? "E_COMMERCE" : "ENCAISSEMENT";
    }

    private Integer parseOptionalInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Le nombre de TPE est invalide.");
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

    private String generateAccountSecret() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }
}
