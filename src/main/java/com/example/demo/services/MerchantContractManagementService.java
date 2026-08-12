package com.example.demo.services;

import com.example.demo.dto.AffiliationActionResponse;
import com.example.demo.dto.ContractSignatureVerificationResponse;
import com.example.demo.dto.MerchantContractOverviewResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MerchantContractManagementService {

    private final UtilisateurRepository utilisateurRepository;
    private final CommercantRepository commercantRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;
    private final ContratSignatureDetector contratSignatureDetector;
    private final JwtService jwtService;
    private final StaffAffiliationManagementService staffAffiliationManagementService;

    public MerchantContractManagementService(
        UtilisateurRepository utilisateurRepository,
        CommercantRepository commercantRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        ServiceDocumentContratAffiliation serviceDocumentContratAffiliation,
        ContratSignatureDetector contratSignatureDetector,
        JwtService jwtService,
        StaffAffiliationManagementService staffAffiliationManagementService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercantRepository = commercantRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.serviceDocumentContratAffiliation = serviceDocumentContratAffiliation;
        this.contratSignatureDetector = contratSignatureDetector;
        this.jwtService = jwtService;
        this.staffAffiliationManagementService = staffAffiliationManagementService;
    }

    @Transactional(readOnly = true)
    public MerchantContractOverviewResponse getLatestContract(String authorizationHeader) {
        dossier_affiliation dossier = readLatestMerchantDossier(authorizationHeader);
        return mapContractOverview(dossier);
    }

    @Transactional(readOnly = true)
    public ContratTelecharge downloadLatestContratGenere(String authorizationHeader) {
        dossier_affiliation dossier = readLatestMerchantDossier(authorizationHeader);
        return toContratTelecharge(serviceDocumentContratAffiliation.telechargerFichier(dossier.getGeneratedContractPath()));
    }

    public ContractSignatureVerificationResponse verifySignature(MultipartFile file) {
        if (!contratSignatureDetector.estFichierContratLanaCash(file)) {
            return new ContractSignatureVerificationResponse(false,
                "Ce fichier ne correspond pas au contrat d'affiliation Lana Cash. "
                    + "Déposez uniquement le contrat PDF généré par la plateforme.");
        }
        boolean signed = contratSignatureDetector.estZoneSignatureRemplie(file);
        String message = signed
            ? "Signature détectée : le contrat semble correctement signé."
            : "Aucune signature détectée : la zone de signature adhérent est vide. "
                + "Apposez votre cachet et signature (précédés de \"lu et approuvé\") avant de soumettre.";
        return new ContractSignatureVerificationResponse(signed, message);
    }

    public AffiliationActionResponse uploadSignedContract(
        String authorizationHeader,
        MultipartFile file
    ) {
        dossier_affiliation dossier = readLatestMerchantDossier(authorizationHeader);
        if (!isSignedContractUploadAllowed(dossier.getStatus())) {
            throw new IllegalArgumentException(
                "Le contrat signé ne peut etre depose qu'après generation du contrat."
            );
        }

        if (!contratSignatureDetector.estFichierContratLanaCash(file, dossier.getTypeAffiliation())) {
            throw new IllegalArgumentException(
                "Ce fichier ne correspond pas au contrat d'affiliation Lana Cash. "
                    + "Déposez uniquement le contrat PDF généré par la plateforme."
            );
        }

        int expectedSignedSections = serviceDocumentContratAffiliation.resolveExpectedSignatureSections(dossier);
        if (!contratSignatureDetector.estZoneSignatureRemplie(file, expectedSignedSections)) {
            throw new IllegalArgumentException(
                "Contrat non signé : la zone de signature de l'adhérent est vide sur au moins une section du contrat. "
                    + "Veuillez apposer votre cachet et signature (précédés de la mention "
                    + "\"lu et approuvé\") sur chaque section avant de redéposer le contrat."
            );
        }

        ServiceDocumentContratAffiliation.ContratSigneEnregistre contratSigneEnregistre =
            serviceDocumentContratAffiliation.enregistrerContratSigne(dossier, file);
        dossier.setSignedContractPath(contratSigneEnregistre.cheminStocke());
        dossier.setSignedContractFileName(contratSigneEnregistre.nomFichierOriginal());
        dossier.setSignedContractUploadedAt(contratSigneEnregistre.dateDepot());

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        return new AffiliationActionResponse(
            "Le contrat signé a été validé automatiquement. Votre espace commerçant est maintenant débloqué."
        );
    }

    private boolean isSignedContractUploadAllowed(StatusDossier status) {
        return status == StatusDossier.CONTRAT_A_SIGNER;
    }

    private MerchantContractOverviewResponse mapContractOverview(dossier_affiliation dossier) {
        return new MerchantContractOverviewResponse(
            dossier.getIdDossier(),
            dossier.getStatus() == null ? "" : dossier.getStatus().name(),
            serviceDocumentContratAffiliation.fichierDisponible(dossier.getGeneratedContractPath()),
            safe(dossier.getGeneratedContractFileName()),
            dossier.getGeneratedContractAt(),
            serviceDocumentContratAffiliation.fichierDisponible(dossier.getSignedContractPath()),
            safe(dossier.getSignedContractFileName()),
            dossier.getSignedContractUploadedAt(),
            dossier.getCommerciale() == null
                ? ""
                : firstNotBlank(
                    (safe(dossier.getCommerciale().getPrenom()) + " " + safe(dossier.getCommerciale().getNom())).trim(),
                    dossier.getCommerciale().getUtilisateur() == null
                        ? ""
                        : dossier.getCommerciale().getUtilisateur().getEmail()
                )
        );
    }

    private dossier_affiliation readLatestMerchantDossier(String authorizationHeader) {
        utilisateur utilisateur = readAuthenticatedMerchant(authorizationHeader);
        commercant commercant = commercantRepository.findByUtilisateur_Id(utilisateur.getId())
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucun commerçant lie a cet utilisateur."
                )
            );

        return dossierAffiliationRepository
            .findFirstByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
                commercant.getIdCommercant()
            )
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Aucun dossier d'affiliation trouve pour ce commerçant."
                )
            );
    }

    private utilisateur readAuthenticatedMerchant(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(
                () -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentification JWT requise."
                )
            );

        if (jwtService.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT expire.");
        }

        Long utilisateurId = jwtService.extractUserId(token);
        if (utilisateurId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalide.");
        }

        utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session introuvable."));

        if (jwtService.isSessionInvalidated(token, utilisateur)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session JWT invalidee.");
        }

        if (utilisateur.getRole() != RoleUser.COMMERCANT) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Seul le commerçant peut acceder a son contrat."
            );
        }

        if (!Boolean.TRUE.equals(utilisateur.getActive())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Activez votre compte pour acceder au contrat."
            );
        }

        return utilisateur;
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

    public record ContratTelecharge(String nomFichier, String typeContenu, byte[] contenu) {
    }

    private ContratTelecharge toContratTelecharge(
        ServiceDocumentContratAffiliation.ContratTelecharge contractDownload
    ) {
        return new ContratTelecharge(
            contractDownload.nomFichier(),
            contractDownload.typeContenu(),
            contractDownload.contenu()
        );
    }
}
