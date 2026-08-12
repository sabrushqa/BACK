package com.example.demo.services;

import com.example.demo.dto.ActivationAccountRequest;
import com.example.demo.dto.ChatbotMerchantProfileResponse;
import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.dto.PasswordResetChallengeResponse;
import com.example.demo.dto.PasswordResetRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.transactions;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.TransactionsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MerchantAccessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerchantAccessService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final CommercantRepository commercantRepository;
    private final BackOfficeRepository backOfficeRepository;
    private final CommercialeRepository commercialeRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final PdvRepository pdvRepository;
    private final SousCommercantRepository sousCommercantRepository;
    private final TpeRepository tpeRepository;
    private final TransactionsRepository transactionsRepository;
    private final SwitchMonetiqueClient switchMonetiqueClient;
    private final PasswordHashService passwordHashService;
    private final JwtService jwtService;
    private final KeycloakAdminService keycloakAdminService;
    private final long passwordResetExpirationMinutes;
    private final String frontendBaseUrl;

    public MerchantAccessService(
        UtilisateurRepository utilisateurRepository,
        CommercantRepository commercantRepository,
        BackOfficeRepository backOfficeRepository,
        CommercialeRepository commercialeRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        PdvRepository pdvRepository,
        SousCommercantRepository sousCommercantRepository,
        TpeRepository tpeRepository,
        TransactionsRepository transactionsRepository,
        SwitchMonetiqueClient switchMonetiqueClient,
        PasswordHashService passwordHashService,
        JwtService jwtService,
        KeycloakAdminService keycloakAdminService,
        @Value("${app.auth.password-reset.expiration-minutes:15}") long passwordResetExpirationMinutes,
        @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercantRepository = commercantRepository;
        this.backOfficeRepository = backOfficeRepository;
        this.commercialeRepository = commercialeRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.pdvRepository = pdvRepository;
        this.sousCommercantRepository = sousCommercantRepository;
        this.tpeRepository = tpeRepository;
        this.transactionsRepository = transactionsRepository;
        this.switchMonetiqueClient = switchMonetiqueClient;
        this.passwordHashService = passwordHashService;
        this.jwtService = jwtService;
        this.keycloakAdminService = keycloakAdminService;
        this.passwordResetExpirationMinutes = passwordResetExpirationMinutes;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
    }

    public MerchantSessionResponse activateAccount(ActivationAccountRequest request) {
        requireText(request.email(), "L'e-mail est obligatoire.");
        requireText(
            request.temporaryPassword(),
            "Le mot de passe temporaire est obligatoire."
        );
        requireText(request.newPassword(), "Le nouveau mot de passe est obligatoire.");

        if (request.newPassword().trim().length() < 8) {
            throw new IllegalArgumentException(
                "Le nouveau mot de passe doit contenir au moins 8 caracteres."
            );
        }

        utilisateur utilisateur = findByEmailForCredentials(request.email());

        if (utilisateur.getDateDesactivation() != null) {
            throw new IllegalArgumentException(
                "Ce compte a été desactive. Contactez le superviseur."
            );
        }

        LocalDateTime expiration = utilisateur.getPasswordExpiresAt();
        if (expiration == null && !Boolean.TRUE.equals(utilisateur.getActive())) {
            throw new IllegalArgumentException(
                "Votre demande est encore en cours de traitement. Vous recevrez un e-mail d'activation après intervention du commercial."
            );
        }

        if (expiration != null && expiration.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                "Le mot de passe temporaire a expire. Merci de demander un nouvel envoi."
            );
        }

        validateTemporaryActivationPassword(request.temporaryPassword(), utilisateur);

        try {
            keycloakAdminService.setPermanentPassword(utilisateur, request.newPassword().trim());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Activation Keycloak impossible. Vérifiez la configuration admin Keycloak et l'existence de cet utilisateur dans Keycloak.",
                exception
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Activation Keycloak impossible. Vérifiez que Keycloak est démarré et que le client portail-affiliation existe.",
                exception
            );
        }
        utilisateur.setPassword(null);
        utilisateur.setActive(Boolean.TRUE);
        utilisateur.setDateActivation(LocalDate.now());
        utilisateur.setPasswordExpiresAt(null);
        utilisateur.setTokenVersion(resolveTokenVersion(utilisateur) + 1);
        utilisateurRepository.save(utilisateur);

        return buildSessionResponse(utilisateur, "Votre compte a été activé avec succès. Connectez-vous avec votre nouveau mot de passe.");
    }

    public PasswordResetChallengeResponse requestPasswordReset(PasswordResetRequest request) {
        requireText(request.email(), "L'e-mail est obligatoire.");

        utilisateur utilisateur = utilisateurRepository
            .findByEmailIgnoreCase(normalize(request.email()))
            .orElseThrow(
                () -> new IllegalArgumentException("Aucun compte actif n'est associe a cette adresse e-mail.")
            );

        if (!Boolean.TRUE.equals(utilisateur.getActive())) {
            if (utilisateur.getDateDesactivation() != null) {
                throw new IllegalArgumentException(
                    "Ce compte a été desactive. Contactez le superviseur."
                );
            }

            if (utilisateur.getPasswordExpiresAt() == null) {
                throw new IllegalArgumentException(
                    "Ce compte est en attente de traitement commercial. Vous recevrez un e-mail d'activation après validation du dossier."
                );
            }

            throw new IllegalArgumentException(
                "Ce compte n'est pas encore actif. Utilisez d'abord l'activation du compte."
            );
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(passwordResetExpirationMinutes);
        boolean emailSent = keycloakAdminService.sendPasswordSetupEmail(
            utilisateur,
            frontendBaseUrl + "/login"
        );

        if (!emailSent) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "L'e-mail de réinitialisation Keycloak n'a pas pu etre envoyé. Vérifiez la configuration SMTP Keycloak."
            );
        }

        return new PasswordResetChallengeResponse(
            "Un e-mail Keycloak de réinitialisation du mot de passe a été envoyé.",
            toOffsetDateTime(expiresAt),
            maskEmail(utilisateur.getEmail())
        );
    }

    @Transactional(readOnly = true)
    public MerchantSessionResponse currentSession(String authorizationHeader) {
        utilisateur utilisateur = readAuthenticatedUser(authorizationHeader);
        return buildSessionResponse(utilisateur, "Session activé.");
    }

    /**
     * Resout le commercantId "effectif" a partir du JWT du navigateur — c'est
     * la seule source fiable de merchant_id : le chatbot (FastAPI) ne doit
     * jamais faire confiance a un merchant_id fourni par le navigateur
     * lui-meme (voir ChatbotProxyController, qui injecte ce commercantId
     * server-side avant de relayer chaque appel). Pour un sous-commerçant,
     * on remonte au commercant parent : les PDV/TPE lui appartiennent, pas
     * au sous-commerçant. Retourne null (jamais d'exception) si le token est
     * absent/invalide ou si l'utilisateur n'est pas un commerçant — dans ce
     * cas le proxy relaie simplement l'appel sans merchant_id, comme avant.
     */
    @Transactional(readOnly = true)
    public Long resolveAuthenticatedCommercantId(String authorizationHeader) {
        utilisateur utilisateur;
        try {
            utilisateur = readAuthenticatedUser(authorizationHeader);
        } catch (ResponseStatusException ex) {
            return null;
        }

        if (utilisateur.getRole() == RoleUser.SOUS_COMMERCANT) {
            return sousCommercantRepository.findByUtilisateur_Id(utilisateur.getId())
                .map(sousCommercant -> {
                    commercant parent = sousCommercant.getCommercant();
                    if (parent == null) {
                        parent = pdvRepository
                            .findTop8BySousCommercant_Utilisateur_IdOrderByIdPDVDesc(utilisateur.getId())
                            .stream()
                            .findFirst()
                            .map(pdv::getCommercant)
                            .orElse(null);
                    }
                    return parent != null ? parent.getIdCommercant() : null;
                })
                .orElse(null);
        }

        return commercantRepository.findByUtilisateur_Id(utilisateur.getId())
            .map(commercant::getIdCommercant)
            .orElse(null);
    }

    /**
     * Profil commerçant expose au chatbot (endpoint interne). Reutilise la
     * meme fusion TPE local+Oracle que buildSessionResponse pour que le
     * chatbot voie exactement les memes TPE que le commerçant dans son
     * propre espace — pas une vue partielle/differente.
     */
    @Transactional(readOnly = true)
    public ChatbotMerchantProfileResponse getMerchantProfileForChatbot(Long commercantId) {
        commercant commercant = commercantRepository.findById(commercantId).orElse(null);
        if (commercant == null) {
            return null;
        }

        dossier_affiliation workspaceDossier = resolveWorkspaceDossier(commercant);
        TypeAffiliation typeAffiliation = workspaceDossier != null ? workspaceDossier.getTypeAffiliation() : null;
        boolean supportsTpe = typeAffiliation == null || typeAffiliation != TypeAffiliation.E_COMMERCE;
        boolean supportsEcommerce = typeAffiliation == TypeAffiliation.E_COMMERCE
            || typeAffiliation == TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE;

        List<pdv> pdvs = pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(commercantId);
        List<tpe> localTpes = tpeRepository.findByCommercantIdOrderByIdDesc(commercantId);

        List<ChatbotMerchantProfileResponse.PdvItem> pdvItems = pdvs.stream()
            .map(p -> new ChatbotMerchantProfileResponse.PdvItem(
                p.getIdPDV(),
                p.getNomPDV(),
                p.getVille(),
                p.getAdresse()
            ))
            .toList();

        List<ChatbotMerchantProfileResponse.TpeItem> tpeItems = buildTpeItemsForCommercant(localTpes, commercantId, pdvs)
            .stream()
            .map(item -> new ChatbotMerchantProfileResponse.TpeItem(
                item.numeroSerie(),
                item.modele(),
                item.pdvId(),
                item.pdv()
            ))
            .toList();

        return new ChatbotMerchantProfileResponse(
            commercantId,
            typeAffiliation != null ? typeAffiliation.name() : null,
            supportsTpe,
            supportsEcommerce,
            pdvItems,
            tpeItems
        );
    }

    private MerchantSessionResponse buildSessionResponse(
        utilisateur utilisateur,
        String message
    ) {
        if (isStaffWorkspaceRole(utilisateur.getRole())) {
            return buildStaffWorkspaceSessionResponse(utilisateur, message);
        }

        if (utilisateur.getRole() == RoleUser.SOUS_COMMERCANT) {
            return buildSubMerchantSessionResponse(utilisateur, message);
        }

        commercant commercant = commercantRepository.findByUtilisateur_Id(utilisateur.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucun commerçant lie a cet utilisateur."
                )
            );

        Long commercantId = commercant.getIdCommercant();
        dossier_affiliation workspaceDossier = resolveWorkspaceDossier(commercant);

        List<pdv> pdvs = pdvRepository.findByCommercant_IdCommercantOrderByIdPDVAsc(
            commercantId
        );
        List<tpe> tpes = tpeRepository.findByCommercantIdOrderByIdDesc(
            commercantId
        );
        List<transactions> transactions =
            transactionsRepository
                .findTop8ByTpe_Pdv_Commercant_IdCommercantOrderByDateTransactionDescHeureTransactionDesc(
                    commercantId
                );
        List<sous_commercant> sousCommercants = pdvRepository
            .findDistinctSousCommercantsByCommercantId(commercantId);

        String displayName = firstNotBlank(
            commercant.getNomCommercial(),
            commercant.getRaisonSociale(),
            utilisateur.getEmail()
        );
        String dossierStatus = workspaceDossier != null && workspaceDossier.getStatus() != null
            ? workspaceDossier.getStatus().name()
            : "";
        // Un dossier ACCEPTE ne suffit pas à débloquer l'espace commerçant si son
        // type d'affiliation implique un encaissement physique (TPE, SOFTPOS,
        // QR_CODE, ENCAISSEMENT_ET_ECOMMERCE) : il faut en plus qu'un TPE ait été
        // réellement affecté par le BOA (voir SupervisorManagementService::
        // assignTpeToCommercant). Seul le E_COMMERCE pur n'a pas besoin de TPE.
        boolean requiresTpeAssignment = workspaceDossier != null
            && workspaceDossier.getTypeAffiliation() != null
            && workspaceDossier.getTypeAffiliation() != TypeAffiliation.E_COMMERCE;
        // tpes (table locale) ne couvre que les TPE auto-provisionnés pour les
        // demandes "nouveau PDV" (provisionRequestedTerminals). Le flux BOA
        // principal (assignTpeToCommercant) affecte le TPE UNIQUEMENT côté
        // Oracle via switchMonetiqueClient — sans cette vérification, le
        // workspace ne se débloquait jamais pour ce flux malgré un TPE
        // réellement affecté.
        boolean hasAssignedTpe = !tpes.isEmpty() || hasTpeAssignedInOracle(commercantId);
        boolean workspaceUnlocked = workspaceDossier != null
            && workspaceDossier.getStatus() == StatusDossier.ACCEPTE
            && (!requiresTpeAssignment || hasAssignedTpe);
        String typeAffiliation = workspaceDossier != null && workspaceDossier.getTypeAffiliation() != null
            ? workspaceDossier.getTypeAffiliation().name()
            : "";
        // Tokens gérés par Keycloak côté client — pas de token interne généré ici.
        String accessToken = null;
        OffsetDateTime tokenExpiration = null;

        return new MerchantSessionResponse(
            utilisateur.getId(),
            commercantId,
            safe(utilisateur.getEmail()),
            displayName,
            utilisateur.getRole().name(),
            Boolean.TRUE.equals(utilisateur.getActive()),
            dossierStatus,
            workspaceUnlocked,
            workspaceDossier == null ? "" : safe(workspaceDossier.getMotifRefus()),
            commercant.getType() == null ? "" : commercant.getType().name(),
            typeAffiliation,
            message,
            accessToken,
            accessToken == null ? null : "Bearer",
            tokenExpiration,
            new MerchantSessionResponse.Summary(
                transactionsRepository.countByTpe_Pdv_Commercant_IdCommercant(commercantId),
                pdvRepository.countByCommercant_IdCommercant(commercantId),
                tpeRepository.countByPdv_Commercant_IdCommercant(commercantId),
                sousCommercants.size()
            ),
            new MerchantSessionResponse.Profile(
                displayName,
                safe(utilisateur.getEmail()),
                safe(commercant.getTelephone()),
                safe(commercant.getVille()),
                safe(commercant.getRegion()),
                safe(commercant.getActivite()),
                commercant.getType() == null ? "" : commercant.getType().name(),
                typeAffiliation,
                workspaceDossier == null ? "" : safe(workspaceDossier.getSiteMarchandUrl()),
                workspaceDossier == null ? "" : safe(workspaceDossier.getApplicationMobile()),
                workspaceDossier == null ? "" : safe(workspaceDossier.getModeServiceEcommerce())
            ),
            sousCommercants.stream()
                .map(
                    sousCommercant -> {
                        pdv assignedPdv = findAssignedPdv(pdvs, sousCommercant);
                        utilisateur subUser = sousCommercant.getUtilisateur();
                        return new MerchantSessionResponse.SubMerchantItem(
                            sousCommercant.getIdSousCommercant(),
                            safe(sousCommercant.getNom()),
                            safe(sousCommercant.getPrenom()),
                            safe(sousCommercant.getEmail()),
                            safe(sousCommercant.getTelephone()),
                            safe(sousCommercant.getStatut()),
                            subUser == null ? null : Boolean.TRUE.equals(subUser.getActive()),
                            assignedPdv == null ? null : assignedPdv.getIdPDV(),
                            assignedPdv == null ? "" : safe(assignedPdv.getNomPDV()),
                            safe(sousCommercant.getCanalEcommerce())
                        );
                    }
                )
                .toList(),
            pdvs.stream()
                .map(
                    pdv -> toPdvItem(pdv, workspaceUnlocked)
                )
                .toList(),
            buildTpeItemsForCommercant(tpes, commercantId, pdvs),
            transactions.stream()
                .map(
                    transaction ->
                        new MerchantSessionResponse.TransactionItem(
                            transaction.getIdTransaction(),
                            transaction.getDateTransaction() == null
                                ? ""
                                : transaction.getDateTransaction().toString(),
                            transaction.getHeureTransaction() == null
                                ? ""
                                : transaction.getHeureTransaction().toString(),
                            transaction.getMontant(),
                            safe(transaction.getDevise()),
                            safe(transaction.getStatutTransaction()),
                            safe(transaction.getTypePaiement()),
                            transaction.getTpe() == null ? "" : safe(transaction.getTpe().getNumeroSerie()),
                            transaction.getTpe() == null || transaction.getTpe().getPdv() == null
                                ? null
                                : transaction.getTpe().getPdv().getIdPDV(),
                            transaction.getTpe() == null || transaction.getTpe().getPdv() == null
                                ? ""
                                : safe(transaction.getTpe().getPdv().getNomPDV())
                        )
                )
                .toList(),
            true,
            true,
            true
        );
    }

    private MerchantSessionResponse buildSubMerchantSessionResponse(
        utilisateur utilisateur,
        String message
    ) {
        sous_commercant sousCommercant = sousCommercantRepository
            .findByUtilisateur_Id(utilisateur.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucun sous-commerçant lie a cet utilisateur."
                )
            );
        List<pdv> pdvs = pdvRepository.findTop8BySousCommercant_Utilisateur_IdOrderByIdPDVDesc(
            utilisateur.getId()
        );
        List<tpe> tpes = tpeRepository
            .findBySubMerchantUserIdOrderByIdDesc(utilisateur.getId());
        List<transactions> transactions = transactionsRepository
            .findTop8ByTpe_Pdv_SousCommercant_Utilisateur_IdOrderByDateTransactionDescHeureTransactionDesc(
                utilisateur.getId()
            );
        String displayName = firstNotBlank(
            (safe(sousCommercant.getPrenom()) + " " + safe(sousCommercant.getNom())).trim(),
            utilisateur.getEmail(),
            "Sous-commerçant"
        );

        // A pdv-attached sous-commerçant's parent is found via its pdv (TPE case);
        // an e-commerce sous-commerçant instead carries a direct commercant link
        // (see sous_commercant.commercant), since it has no pdv row at all.
        commercant parentCommercant = sousCommercant.getCommercant();
        if (parentCommercant == null) {
            parentCommercant = pdvs.stream().findFirst().map(pdv::getCommercant).orElse(null);
        }
        dossier_affiliation workspaceDossier = parentCommercant == null
            ? null
            : resolveWorkspaceDossier(parentCommercant);
        String typeAffiliation = workspaceDossier != null && workspaceDossier.getTypeAffiliation() != null
            ? workspaceDossier.getTypeAffiliation().name()
            : "TPE";

        // Tokens gérés par Keycloak côté client — pas de token interne généré ici.
        String accessToken = null;
        OffsetDateTime tokenExpiration = null;

        return new MerchantSessionResponse(
            utilisateur.getId(),
            0L,
            safe(utilisateur.getEmail()),
            displayName,
            utilisateur.getRole().name(),
            Boolean.TRUE.equals(utilisateur.getActive()),
            "ACCEPTE",
            true,
            "",
            "SOUS_COMMERCANT",
            typeAffiliation,
            message,
            accessToken,
            accessToken == null ? null : "Bearer",
            tokenExpiration,
            new MerchantSessionResponse.Summary(
                transactionsRepository.countByTpe_Pdv_SousCommercant_Utilisateur_Id(utilisateur.getId()),
                pdvRepository.countBySousCommercant_Utilisateur_Id(utilisateur.getId()),
                tpeRepository.countByPdv_SousCommercant_Utilisateur_Id(utilisateur.getId()),
                0
            ),
            new MerchantSessionResponse.Profile(
                displayName,
                safe(utilisateur.getEmail()),
                safe(sousCommercant.getTelephone()),
                pdvs.stream().findFirst().map(pdv::getVille).orElse(""),
                "",
                "",
                "SOUS_COMMERCANT",
                typeAffiliation,
                workspaceDossier == null ? "" : safe(workspaceDossier.getSiteMarchandUrl()),
                workspaceDossier == null ? "" : safe(workspaceDossier.getApplicationMobile()),
                workspaceDossier == null ? "" : safe(workspaceDossier.getModeServiceEcommerce())
            ),
            List.of(),
            pdvs.stream()
                .map(
                    pdv -> toPdvItem(pdv, true)
                )
                .toList(),
            buildTpeItemsForSubMerchant(tpes, pdvs),
            transactions.stream()
                .map(
                    transaction ->
                        new MerchantSessionResponse.TransactionItem(
                            transaction.getIdTransaction(),
                            transaction.getDateTransaction() == null
                                ? ""
                                : transaction.getDateTransaction().toString(),
                            transaction.getHeureTransaction() == null
                                ? ""
                                : transaction.getHeureTransaction().toString(),
                            transaction.getMontant(),
                            safe(transaction.getDevise()),
                            safe(transaction.getStatutTransaction()),
                            safe(transaction.getTypePaiement()),
                            transaction.getTpe() == null ? "" : safe(transaction.getTpe().getNumeroSerie()),
                            transaction.getTpe() == null || transaction.getTpe().getPdv() == null
                                ? null
                                : transaction.getTpe().getPdv().getIdPDV(),
                            transaction.getTpe() == null || transaction.getTpe().getPdv() == null
                                ? ""
                                : safe(transaction.getTpe().getPdv().getNomPDV())
                        )
                )
                .toList(),
            true,
            true,
            true
        );
    }

    private MerchantSessionResponse.PdvItem toPdvItem(pdv pdv, boolean forceActiveStatus) {
        sous_commercant sousCommercant = pdv.getSousCommercant();
        utilisateur subUser = sousCommercant == null ? null : sousCommercant.getUtilisateur();
        String subMerchantName = sousCommercant == null
            ? ""
            : firstNotBlank(
                (safe(sousCommercant.getPrenom()) + " " + safe(sousCommercant.getNom())).trim(),
                sousCommercant.getEmail()
            );

        String statutPdv = safe(pdv.getStatut());
        if (forceActiveStatus && !StringUtils.hasText(statutPdv)) {
            statutPdv = "ACTIF";
        }

        return new MerchantSessionResponse.PdvItem(
            pdv.getIdPDV(),
            safe(pdv.getNomPDV()),
            safe(pdv.getVille()),
            safe(pdv.getAdresse()),
            safe(pdv.getTelephone()),
            statutPdv,
            safe(pdv.getEmail()),
            safe(pdv.getCodePostal()),
            pdv.getDateCreation() == null ? "" : pdv.getDateCreation().toString(),
            sousCommercant == null ? null : sousCommercant.getIdSousCommercant(),
            subMerchantName,
            sousCommercant == null ? "" : safe(sousCommercant.getEmail()),
            sousCommercant == null ? "" : safe(sousCommercant.getStatut()),
            subUser == null ? null : Boolean.TRUE.equals(subUser.getActive())
        );
    }

    private boolean isNewPdvProductRequest(dossier_affiliation dossier) {
        return dossier != null
            && "NOUVEAU_PDV".equalsIgnoreCase(safe(dossier.getOrigineCreation()).trim());
    }

    /**
     * Vérifie côté Oracle (switch-monetique-service) si au moins un TPE est
     * affecté à ce commerçant — c'est la vraie source de vérité pour le flux
     * BOA principal (assignTpeToCommercant), contrairement à la table locale
     * "tpe" qui ne couvre que le provisionnement auto pour NOUVEAU_PDV.
     * Dégradation gracieuse : si switch-monetique-service est injoignable, on
     * logue et on considère qu'aucun TPE Oracle n'est visible plutôt que de
     * faire planter la session/le dashboard du commerçant pour une panne d'un
     * service tiers non critique pour l'affichage.
     */
    private boolean hasTpeAssignedInOracle(Long commercantId) {
        if (commercantId == null) {
            return false;
        }
        try {
            String idCommercant = commercantId.toString();
            return switchMonetiqueClient.stockComplet().stream()
                .anyMatch(tpe -> idCommercant.equals(tpe.idCommercant()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "[MerchantAccessService] switch-monetique-service injoignable, "
                    + "impossible de vérifier l'affectation TPE Oracle pour le commerçant {}.",
                commercantId,
                exception
            );
            return false;
        }
    }

    /**
     * Recupere le stock Oracle complet, en degradant gracieusement (log +
     * liste vide) si switch-monetique-service est injoignable — la liste de
     * TPE du commercant ne doit pas faire planter sa session pour une panne
     * d'un service tiers.
     */
    private List<SwitchMonetiqueClient.SwitchTpe> fetchOracleStockSafely() {
        try {
            return switchMonetiqueClient.stockComplet();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "[MerchantAccessService] switch-monetique-service injoignable, "
                    + "liste de TPE Oracle indisponible pour cette session.",
                exception
            );
            return List.of();
        }
    }

    private MerchantSessionResponse.TpeItem toLocalTpeItem(tpe terminal) {
        return new MerchantSessionResponse.TpeItem(
            String.valueOf(terminal.getIdTPE()),
            safe(terminal.getNumeroSerie()),
            safe(terminal.getModele()),
            safe(terminal.getStatut()),
            safe(terminal.getTypeConnexion()),
            terminal.getPdv() == null ? null : terminal.getPdv().getIdPDV(),
            terminal.getPdv() == null ? "" : safe(terminal.getPdv().getNomPDV())
        );
    }

    private List<MerchantSessionResponse.TpeItem> mapOracleTpeItems(
        List<SwitchMonetiqueClient.SwitchTpe> oracleStock,
        Predicate<SwitchMonetiqueClient.SwitchTpe> filter,
        List<pdv> visiblePdvs
    ) {
        Map<String, pdv> pdvById = visiblePdvs.stream()
            .collect(Collectors.toMap(p -> p.getIdPDV().toString(), p -> p, (a, b) -> a));
        return oracleStock.stream()
            .filter(filter)
            .map(oracleTpe -> {
                pdv matchedPdv = oracleTpe.idPdv() == null ? null : pdvById.get(oracleTpe.idPdv());
                return new MerchantSessionResponse.TpeItem(
                    oracleTpe.idTpe(),
                    safe(oracleTpe.idTpe()),
                    safe(oracleTpe.nature()),
                    oracleTpe.actif() ? "ACTIF" : "INACTIF",
                    safe(oracleTpe.connectivite()),
                    matchedPdv == null ? null : matchedPdv.getIdPDV(),
                    matchedPdv == null ? "" : safe(matchedPdv.getNomPDV())
                );
            })
            .toList();
    }

    /**
     * Combine les TPE locaux (auto-provisionnes pour NOUVEAU_PDV) et les TPE
     * Oracle reellement affectes par le BOA (assignTpeToCommercant) pour un
     * commercant complet — sans cette fusion, seuls les TPE auto-provisionnes
     * apparaissaient dans son espace, jamais ceux affectes via le flux
     * principal.
     */
    private List<MerchantSessionResponse.TpeItem> buildTpeItemsForCommercant(
        List<tpe> localTpes,
        Long commercantId,
        List<pdv> pdvs
    ) {
        List<MerchantSessionResponse.TpeItem> items = new ArrayList<>(localTpes.stream().map(this::toLocalTpeItem).toList());
        if (commercantId != null) {
            String idCommercant = commercantId.toString();
            items.addAll(mapOracleTpeItems(
                fetchOracleStockSafely(),
                oracleTpe -> idCommercant.equals(oracleTpe.idCommercant()),
                pdvs
            ));
        }
        return items;
    }

    /**
     * Meme fusion pour un sous-commerçant, mais scope par appartenance aux
     * PDV visibles (pas par commercant_id, puisqu'un sous-commerçant ne voit
     * qu'une partie des PDV du commerçant principal).
     */
    private List<MerchantSessionResponse.TpeItem> buildTpeItemsForSubMerchant(
        List<tpe> localTpes,
        List<pdv> visiblePdvs
    ) {
        List<MerchantSessionResponse.TpeItem> items = new ArrayList<>(localTpes.stream().map(this::toLocalTpeItem).toList());
        Set<String> visiblePdvIds = visiblePdvs.stream().map(p -> p.getIdPDV().toString()).collect(Collectors.toSet());
        items.addAll(mapOracleTpeItems(
            fetchOracleStockSafely(),
            oracleTpe -> oracleTpe.idPdv() != null && visiblePdvIds.contains(oracleTpe.idPdv()),
            visiblePdvs
        ));
        return items;
    }

    private dossier_affiliation resolveWorkspaceDossier(commercant commercant) {
        List<dossier_affiliation> merchantDossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
                commercant.getIdCommercant()
            );
        dossier_affiliation latestDossier = merchantDossiers.stream()
            .findFirst()
            .orElse(null);
        return merchantDossiers.stream()
            .filter(dossier -> !isNewPdvProductRequest(dossier))
            .findFirst()
            .orElse(latestDossier);
    }

    private pdv findAssignedPdv(List<pdv> pdvs, sous_commercant sousCommercant) {
        if (sousCommercant == null) {
            return null;
        }
        return pdvs.stream()
            .filter(
                pdv -> pdv.getSousCommercant() != null
                    && Objects.equals(
                        pdv.getSousCommercant().getIdSousCommercant(),
                        sousCommercant.getIdSousCommercant()
                    )
            )
            .findFirst()
            .orElse(null);
    }

    private MerchantSessionResponse buildStaffWorkspaceSessionResponse(
        utilisateur utilisateur,
        String message
    ) {
        StaffWorkspaceDetails workspaceDetails = resolveStaffWorkspaceDetails(utilisateur);
        // Tokens gérés par Keycloak côté client — pas de token interne généré ici.
        String accessToken = null;
        OffsetDateTime tokenExpiration = null;

        return new MerchantSessionResponse(
            utilisateur.getId(),
            0L,
            safe(utilisateur.getEmail()),
            workspaceDetails.displayName(),
            utilisateur.getRole().name(),
            Boolean.TRUE.equals(utilisateur.getActive()),
            "",
            true,
            "",
            workspaceDetails.typeCommercant(),
            workspaceDetails.typeAffiliation(),
            message,
            accessToken,
            accessToken == null ? null : "Bearer",
            tokenExpiration,
            new MerchantSessionResponse.Summary(0, 0, 0, 0),
            new MerchantSessionResponse.Profile(
                workspaceDetails.displayName(),
                safe(utilisateur.getEmail()),
                workspaceDetails.telephone(),
                "",
                workspaceDetails.region(),
                workspaceDetails.activite(),
                workspaceDetails.typeCommercant(),
                workspaceDetails.typeAffiliation(),
                "",
                "",
                ""
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            workspaceDetails.peutValiderDossiers(),
            workspaceDetails.peutAffecterTpe(),
            workspaceDetails.peutGererReclamations()
        );
    }

    private utilisateur findByEmailForCredentials(String email) {
        requireText(email, "L'e-mail est obligatoire.");

        return utilisateurRepository
            .findByEmailIgnoreCase(normalize(email))
            .orElseThrow(
                () -> new IllegalArgumentException("E-mail ou mot de passe invalide.")
            );
    }

    private void validatePassword(String rawPassword, utilisateur utilisateur) {
        requireText(rawPassword, "Le mot de passe est obligatoire.");

        String trimmedRawPassword = rawPassword.trim();
        boolean wasLegacyPlainText = passwordHashService.isLegacyPlainTextFormat(utilisateur.getPassword());

        if (!passwordHashService.matches(trimmedRawPassword, utilisateur.getPassword())) {
            throw new IllegalArgumentException("E-mail ou mot de passe invalide.");
        }

        // Upgrade-on-login : un mot de passe encore en ancien format clair est
        // immediatement re-hache en PBKDF2 des qu'il a ete verifie avec succes,
        // pour reduire au minimum la duree pendant laquelle il reste en clair en base.
        if (wasLegacyPlainText) {
            utilisateur.setPassword(passwordHashService.hash(trimmedRawPassword));
            utilisateurRepository.save(utilisateur);
        }
    }

    private void validateTemporaryActivationPassword(String rawPassword, utilisateur utilisateur) {
        requireText(rawPassword, "Le mot de passe temporaire est obligatoire.");

        if (StringUtils.hasText(utilisateur.getPassword())) {
            validatePassword(rawPassword, utilisateur);
            return;
        }

        if (!keycloakAdminService.passwordMatches(utilisateur, rawPassword)) {
            throw new IllegalArgumentException("E-mail ou mot de passe invalide.");
        }
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

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "votre adresse e-mail";
        }

        String trimmedEmail = email.trim();
        int atIndex = trimmedEmail.indexOf('@');
        if (atIndex <= 1) {
            return trimmedEmail;
        }

        String localPart = trimmedEmail.substring(0, atIndex);
        String domain = trimmedEmail.substring(atIndex);
        String visiblePrefix = localPart.substring(0, Math.min(2, localPart.length()));

        return visiblePrefix + "***" + domain;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String safe(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private String resolveSupervisorDisplayName(utilisateur utilisateur) {
        String email = safe(utilisateur.getEmail());
        if (!StringUtils.hasText(email)) {
            return "Superviseur Lana Cash";
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }

        String localPart = email.substring(0, atIndex).replace('.', ' ').replace('_', ' ').trim();
        return StringUtils.hasText(localPart) ? localPart : email;
    }

    private StaffWorkspaceDetails resolveStaffWorkspaceDetails(utilisateur utilisateur) {
        return switch (utilisateur.getRole()) {
            case SUPERVISEUR -> new StaffWorkspaceDetails(
                resolveSupervisorDisplayName(utilisateur),
                "SUPERVISEUR",
                "SUPERVISION",
                "Accès superviseur",
                "",
                "",
                true,
                true,
                true
            );
            case BACK_OFFICE -> {
                back_office backOffice = backOfficeRepository
                    .findByUtilisateur_Id(utilisateur.getId())
                    .orElse(null);

                String displayName = backOffice == null
                    ? resolveSupervisorDisplayName(utilisateur)
                    : firstNotBlank(
                        (safe(backOffice.getPrenom()) + " " + safe(backOffice.getNom())).trim(),
                        resolveSupervisorDisplayName(utilisateur)
                    );

                yield new StaffWorkspaceDetails(
                    displayName,
                    "BACK_OFFICE",
                    "OPERATIONS",
                    firstNotBlank(backOffice == null ? "" : backOffice.getService(), "Support back office"),
                    backOffice == null ? "" : safe(backOffice.getService()),
                    "",
                    // Toutes les permissions BOA sont desormais accordees par defaut :
                    // les restrictions individuelles (peutValiderDossiers/peutAffecterTpe/peutGererReclamations)
                    // ont ete supprimees.
                    true,
                    true,
                    true
                );
            }
            case COMMERCIAL -> {
                commerciale commerciale = commercialeRepository
                    .findByUtilisateur_Id(utilisateur.getId())
                    .orElse(null);

                String displayName = commerciale == null
                    ? resolveSupervisorDisplayName(utilisateur)
                    : firstNotBlank(
                        (safe(commerciale.getPrenom()) + " " + safe(commerciale.getNom())).trim(),
                        resolveSupervisorDisplayName(utilisateur)
                    );

                yield new StaffWorkspaceDetails(
                    displayName,
                    "COMMERCIAL",
                    "SUPERVISION",
                    "Suivi commercial",
                    commerciale == null ? "" : safe(commerciale.getRegion()),
                    commerciale == null ? "" : safe(commerciale.getTelephone()),
                    true,
                    true,
                    true
                );
            }
            default -> new StaffWorkspaceDetails(
                resolveSupervisorDisplayName(utilisateur),
                utilisateur.getRole().name(),
                "SUPERVISION",
                "Accès équipe",
                "",
                "",
                true,
                true,
                true
            );
        };
    }

    private boolean isStaffWorkspaceRole(RoleUser role) {
        return role == RoleUser.SUPERVISEUR
            || role == RoleUser.BACK_OFFICE
            || role == RoleUser.COMMERCIAL;
    }

    private Integer resolveTokenVersion(utilisateur utilisateur) {
        return utilisateur.getTokenVersion() == null ? 0 : utilisateur.getTokenVersion();
    }

    private record StaffWorkspaceDetails(
        String displayName,
        String typeCommercant,
        String typeAffiliation,
        String activite,
        String region,
        String telephone,
        boolean peutValiderDossiers,
        boolean peutAffecterTpe,
        boolean peutGererReclamations
    ) {
    }
}
