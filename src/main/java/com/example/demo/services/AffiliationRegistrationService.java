package com.example.demo.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.config.DocumentMimeValidator;
import com.example.demo.dto.AffiliationRegistrationRequest;
import com.example.demo.dto.AffiliationRegistrationResponse;
import com.example.demo.entities.AE;
import com.example.demo.entities.Association;
import com.example.demo.entities.PM;
import com.example.demo.entities.PP;
import com.example.demo.entities.commercant;
import com.example.demo.entities.documents;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDocument;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.enums.TypeDocument;
import com.example.demo.repositories.AERepository;
import com.example.demo.repositories.AssociationRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DocumentsRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PMRepository;
import com.example.demo.repositories.PPRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class AffiliationRegistrationService {
    private static final int MAX_POINTS_VENTE = 10;
    private static final int MAX_TPE = 10;

    private final UtilisateurRepository utilisateurRepository;
    private final CommercantRepository commercantRepository;
    private final PPRepository ppRepository;
    private final PMRepository pmRepository;
    private final AERepository aeRepository;
    private final AssociationRepository associationRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final DocumentsRepository documentsRepository;
    private final PdvRepository pdvRepository;
    private final AffiliationDocumentValidationService affiliationDocumentValidationService;
    private final DocumentMimeValidator documentMimeValidator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PasswordHashService passwordHashService;
    private final AffiliationRequestMailService affiliationRequestMailService;
    private final SupervisorNotificationService supervisorNotificationService;
    private final GeocodingService geocodingService;
    private final Path uploadRoot;

    public AffiliationRegistrationService(
        UtilisateurRepository utilisateurRepository,
        CommercantRepository commercantRepository,
        PPRepository ppRepository,
        PMRepository pmRepository,
        AERepository aeRepository,
        AssociationRepository associationRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        DocumentsRepository documentsRepository,
        PdvRepository pdvRepository,
        AffiliationDocumentValidationService affiliationDocumentValidationService,
        DocumentMimeValidator documentMimeValidator,
        PasswordHashService passwordHashService,
        AffiliationRequestMailService affiliationRequestMailService,
        SupervisorNotificationService supervisorNotificationService,
        GeocodingService geocodingService,
        @Value("${app.affiliation.upload-dir:uploads/affiliations}") String uploadDirectory
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.commercantRepository = commercantRepository;
        this.ppRepository = ppRepository;
        this.pmRepository = pmRepository;
        this.aeRepository = aeRepository;
        this.associationRepository = associationRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.documentsRepository = documentsRepository;
        this.pdvRepository = pdvRepository;
        this.affiliationDocumentValidationService = affiliationDocumentValidationService;
        this.documentMimeValidator = documentMimeValidator;
        this.passwordHashService = passwordHashService;
        this.affiliationRequestMailService = affiliationRequestMailService;
        this.supervisorNotificationService = supervisorNotificationService;
        this.geocodingService = geocodingService;
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    public AffiliationRegistrationResponse submit(
        AffiliationRegistrationRequest request,
        Map<String, MultipartFile> uploadedDocuments
    ) {
        validateRequest(request, uploadedDocuments);
        affiliationDocumentValidationService.validateUploadedDocumentsOrThrow(uploadedDocuments);

        String normalizedEmail = normalize(request.getEmail()).toLowerCase(Locale.ROOT);
        if (utilisateurRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Un utilisateur avec cet e-mail existe déjà.");
        }

        utilisateur utilisateur = buildUtilisateur(normalizedEmail);
        utilisateur = utilisateurRepository.save(utilisateur);

        commercant commercant = buildCommercant(request, utilisateur);
        commercant = commercantRepository.save(commercant);

        saveSpecificMerchantProfile(request, commercant);
        savePointVentes(request, commercant);

        dossier_affiliation dossier = buildDossier(request, commercant);
        dossier = dossierAffiliationRepository.save(dossier);

        int documentsCount = saveDocuments(request, dossier, uploadedDocuments);
        AffiliationRequestMailService.MailDispatchResult mailDispatchResult =
            affiliationRequestMailService.sendSubmissionAcknowledgement(utilisateur, commercant);
        supervisorNotificationService.notifyNewAutoAffiliationRequest(dossier, commercant);

        return new AffiliationRegistrationResponse(
            utilisateur.getId(),
            commercant.getIdCommercant(),
            dossier.getIdDossier(),
            dossier.getStatus().name(),
            "Votre dossier d'affiliation a bien été soumis.",
            documentsCount,
            mailDispatchResult.sent(),
            mailDispatchResult.message()
        );
    }

    private void validateRequest(
        AffiliationRegistrationRequest request,
        Map<String, MultipartFile> uploadedDocuments
    ) {
        requireText(request.getTypeCommercant(), "Le type de commerçant est obligatoire.");
        requireText(request.getTypeAffiliation(), "Le type d'affiliation est obligatoire.");
        requireText(request.getEmail(), "L'e-mail est obligatoire.");
        requireText(request.getTelephonePrincipal(), "Le téléphone principal est obligatoire.");
        requireText(request.getActivite(), "L'activité est obligatoire.");
        requireText(request.getSecteur(), "Le secteur est obligatoire.");
        requireText(request.getAdresse(), "L'adresse est obligatoire.");
        requireText(request.getVille(), "La ville est obligatoire.");
        requireText(request.getRegion(), "La region est obligatoire.");
        requireText(request.getRib(), "Le RIB est obligatoire.");

        switch (mapMerchantType(request.getTypeCommercant())) {
            case PERSONNE_PHYSIQUE -> {
                requireText(request.getNom(), "Le nom du commerçant est obligatoire.");
                requireText(request.getPrenom(), "Le prénom du commerçant est obligatoire.");
                requireText(request.getCin(), "La CIN est obligatoire.");
            }
            case PERSONNE_MORALE -> {
                requireText(request.getRaisonSociale(), "La raison sociale est obligatoire.");
                requireText(request.getRc(), "Le RC est obligatoire.");
                requireText(request.getIce(), "L'ICE est obligatoire.");
                requireText(request.getFormeJuridique(), "La forme juridique est obligatoire.");
                requireText(request.getRepresentantLegal(), "Le representant legal est obligatoire.");
            }
            case AUTO_ENTREPRENEUR -> {
                requireText(request.getNom(), "Le nom est obligatoire.");
                requireText(request.getPrenom(), "Le prénom est obligatoire.");
                requireText(
                    request.getNumeroAutoEntrepreneur(),
                    "Le numéro auto-entrepreneur est obligatoire."
                );
            }
            case ASSOCIATION_FONDATION -> {
                requireText(request.getNomEntite(), "Le nom de l'entite est obligatoire.");
                requireText(request.getRepresentantLegal(), "Le representant legal est obligatoire.");
                requireText(request.getObjet(), "L'objet de l'association est obligatoire.");
            }
        }

        switch (mapAffiliationType(request.getTypeAffiliation())) {
            case TPE -> {
                requireText(
                    request.getModeMiseADispositionTpe(),
                    "Le service TPE est obligatoire."
                );
                requireText(request.getEquipementTpe(), "L'équipement TPE est obligatoire.");
                requireText(request.getConnectiviteTpe(), "La connectivite TPE est obligatoire.");
                requireText(request.getNombreTpe(), "Le nombre de TPE est obligatoire.");
            }
            case E_COMMERCE -> {
                requireText(
                    request.getModeServiceEcommerce(),
                    "Le service e-commerce est obligatoire."
                );
                requireText(
                    request.getSiteMarchandUrl(),
                    "L'URL du site marchand est obligatoire."
                );
            }
            case SOFTPOS, QR_CODE -> {
                requireText(
                    request.getModeleQrSoftpos(),
                    "La configuration QR Code ou SoftPOS est obligatoire."
                );
                requireText(
                    request.getNombreQrSoftpos(),
                    "La quantité SoftPOS / QR Code est obligatoire."
                );
            }
            case ENCAISSEMENT_ET_ECOMMERCE -> {
                requireText(
                    request.getModeServiceEcommerce(),
                    "Le service e-commerce est obligatoire."
                );
                requireText(
                    request.getSiteMarchandUrl(),
                    "L'URL du site marchand est obligatoire."
                );
                boolean champsTpeComplets = StringUtils.hasText(request.getModeMiseADispositionTpe())
                    && StringUtils.hasText(request.getEquipementTpe())
                    && StringUtils.hasText(request.getConnectiviteTpe())
                    && StringUtils.hasText(request.getNombreTpe());
                boolean champsQrSoftposComplets = StringUtils.hasText(request.getModeleQrSoftpos())
                    && StringUtils.hasText(request.getNombreQrSoftpos());
                if (!champsTpeComplets && !champsQrSoftposComplets) {
                    throw new IllegalArgumentException(
                        "Les informations du produit d'encaissement (TPE, SoftPOS ou QR Code) sont obligatoires."
                    );
                }
            }
        }

        for (String documentKey : requiredDocumentKeys(request.getTypeCommercant())) {
            if (!hasProvidedDocument(request, uploadedDocuments, documentKey)) {
                throw new IllegalArgumentException(
                    "Le document obligatoire '" + documentKey + "' est manquant."
                );
            }
        }

        if (mapAffiliationType(request.getTypeAffiliation()) != TypeAffiliation.E_COMMERCE) {
            validatePointVentes(request);
        }
        if (StringUtils.hasText(request.getNombreTpe())) {
            validateIntegerRange(request.getNombreTpe(), "Le nombre de TPE", 1, MAX_TPE);
        }
        if (StringUtils.hasText(request.getNombreQrSoftpos())) {
            validateIntegerRange(request.getNombreQrSoftpos(), "La quantité SoftPOS / QR Code", 1, MAX_TPE);
        }
    }

    private void validatePointVentes(AffiliationRegistrationRequest request) {
        requireText(request.getNombrePointsVente(), "Le nombre de points de vente est obligatoire.");
        int expectedCount = parseInteger(
            request.getNombrePointsVente(),
            "Le nombre de points de vente"
        );
        if (expectedCount < 1 || expectedCount > MAX_POINTS_VENTE) {
            throw new IllegalArgumentException(
                "Le nombre de points de vente doit etre compris entre 1 et 10."
            );
        }

        List<PointVenteRequest> pointVentes = readPointVenteRequests(request.getPointVentesJson());
        if (pointVentes.size() != expectedCount) {
            throw new IllegalArgumentException(
                "Le nombre de fiches point de vente doit correspondre au nombre indique."
            );
        }

        for (int index = 0; index < pointVentes.size(); index++) {
            PointVenteRequest pointVente = pointVentes.get(index);
            String label = " du point de vente " + (index + 1) + ".";
            requireText(pointVente.nom(), "Le nom" + label);
            requireText(pointVente.adresse(), "L'adresse" + label);
            requireText(pointVente.ville(), "La ville" + label);
            requireText(pointVente.telephone(), "Le téléphone" + label);
        }
    }

    private utilisateur buildUtilisateur(String email) {
        utilisateur utilisateur = new utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setPassword(null);
        utilisateur.setRole(RoleUser.COMMERCANT);
        utilisateur.setActive(Boolean.FALSE);
        utilisateur.setDateActivation(null);
        utilisateur.setPasswordExpiresAt(null);
        utilisateur.setTokenVersion(0);
        return utilisateur;
    }

    private void savePointVentes(
        AffiliationRegistrationRequest request,
        commercant commercant
    ) {
        if (mapAffiliationType(request.getTypeAffiliation()) == TypeAffiliation.E_COMMERCE) {
            return;
        }

        List<PointVenteRequest> pointVentes = readPointVenteRequests(request.getPointVentesJson());
        for (PointVenteRequest pointVenteRequest : pointVentes) {
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
            if (pointVenteRequest.latitude() != null && pointVenteRequest.longitude() != null) {
                // Position pointee manuellement sur la mini-carte du formulaire - plus
                // fiable qu'un geocodage texte, on ne retente pas Nominatim dans ce cas.
                pointVente.setLatitude(pointVenteRequest.latitude());
                pointVente.setLongitude(pointVenteRequest.longitude());
            } else {
                geocodingService
                    .geocoder(
                        pointVente.getAdresse(),
                        pointVente.getQuartier(),
                        pointVente.getVille(),
                        pointVente.getCodePostal()
                    )
                    .ifPresent(coordonnees -> {
                        pointVente.setLatitude(coordonnees.latitude());
                        pointVente.setLongitude(coordonnees.longitude());
                    });
            }
            pdvRepository.save(pointVente);
        }
    }

    private List<PointVenteRequest> readPointVenteRequests(String pointVentesJson) {
        if (!StringUtils.hasText(pointVentesJson)) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rawPointVentes = objectMapper.readValue(
                pointVentesJson,
                new TypeReference<List<Map<String, Object>>>() {}
            );
            return rawPointVentes
                .stream()
                .map(
                    item ->
                        new PointVenteRequest(
                            asText(item.get("nom")),
                            asText(item.get("adresse")),
                            asText(item.get("ville")),
                            asText(item.get("codePostal")),
                            asText(item.get("quartier")),
                            firstNotBlank(asText(item.get("téléphone")), asText(item.get("telephone"))),
                            firstNotBlank(asText(item.get("e-mail")), asText(item.get("email"))),
                            asDouble(item.get("latitude")),
                            asDouble(item.get("longitude"))
                        )
                )
                .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Les informations des points de vente sont invalides.");
        }
    }

    private static String asText(Object value) {
        return normalizeText(value == null ? null : value.toString());
    }

    private static Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private commercant buildCommercant(
        AffiliationRegistrationRequest request,
        utilisateur utilisateur
    ) {
        TypeCommercant typeCommercant = mapMerchantType(request.getTypeCommercant());

        commercant commercant = new commercant();
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
        commercant.setEmailContact(normalize(request.getEmail()).toLowerCase(Locale.ROOT));
        commercant.setActivite(normalize(request.getActivite()));
        commercant.setSecteur(normalize(request.getSecteur()));
        commercant.setMcc("");
        commercant.setChainePointVente(normalize(request.getChainePointVente()));
        commercant.setPatente(normalize(request.getPatente()));
        commercant.setFonction(normalize(request.getFonction()));
        commercant.setBeneficiairesEffectifs(normalize(request.getBeneficiairesEffectifs()));
        commercant.setDateNaissance(normalize(request.getDateNaissance()));
        commercant.setNationalite(normalize(request.getNationalite()));
        commercant.setNombrePointsVente(
            StringUtils.hasText(request.getNombrePointsVente())
                ? parseInteger(request.getNombrePointsVente(), "Le nombre de points de vente")
                : null
        );
        commercant.setUtilisateur(utilisateur);
        return commercant;
    }

    private void saveSpecificMerchantProfile(
        AffiliationRegistrationRequest request,
        commercant commercant
    ) {
        switch (commercant.getType()) {
            case PERSONNE_PHYSIQUE -> {
                PP pp = new PP();
                pp.setNom(normalize(request.getNom()));
                pp.setPrenom(normalize(request.getPrenom()));
                pp.setCin(normalize(request.getCin()));
                pp.setCommercant(commercant);
                ppRepository.save(pp);
            }
            case PERSONNE_MORALE -> {
                PM pm = new PM();
                pm.setRaisonSociale(normalize(request.getRaisonSociale()));
                pm.setRegistreCommerce(normalize(request.getRc()));
                pm.setIce(normalize(request.getIce()));
                pm.setFormeJuridique(normalize(request.getFormeJuridique()));
                pm.setRepresentantLegal(normalize(request.getRepresentantLegal()));
                pm.setCommercant(commercant);
                pmRepository.save(pm);
            }
            case AUTO_ENTREPRENEUR -> {
                AE ae = new AE();
                ae.setNom(normalize(request.getNom()));
                ae.setPrenom(normalize(request.getPrenom()));
                ae.setNumeroAutoEntrepreneur(normalize(request.getNumeroAutoEntrepreneur()));
                ae.setCommercant(commercant);
                aeRepository.save(ae);
            }
            case ASSOCIATION_FONDATION -> {
                Association association = new Association();
                association.setNomEntite(normalize(request.getNomEntite()));
                association.setRepresentantLegal(normalize(request.getRepresentantLegal()));
                association.setObjet(normalize(request.getObjet()));
                association.setCommercant(commercant);
                associationRepository.save(association);
            }
        }
    }

    private dossier_affiliation buildDossier(
        AffiliationRegistrationRequest request,
        commercant commercant
    ) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_ASSIGNATION);
        dossier.setOrigineCreation("AUTO_AFFILIATION");
        dossier.setTypeAffiliation(mapAffiliationType(request.getTypeAffiliation()));
        dossier.setRib(normalize(request.getRib()));
        dossier.setDateSoumission(LocalDate.now());
        dossier.setModeMiseADispositionTpe(normalize(request.getModeMiseADispositionTpe()));
        dossier.setNombreTpe(parseOptionalInteger(request.getNombreTpe(), "Le nombre de TPE"));
        dossier.setEquipementTpe(normalize(request.getEquipementTpe()));
        dossier.setConnectiviteTpe(normalize(request.getConnectiviteTpe()));
        dossier.setCommissionLocaleTpe(normalize(request.getCommissionLocaleTpe()));
        dossier.setCommissionEtrangereTpe(normalize(request.getCommissionEtrangereTpe()));
        dossier.setDepotTpe(normalize(request.getDepotTpe()));
        dossier.setPrixAchatTpe(normalize(request.getPrixAchatTpe()));
        dossier.setPrixLicenceTpe(normalize(request.getPrixLicenceTpe()));
        dossier.setModeServiceEcommerce(normalize(request.getModeServiceEcommerce()));
        dossier.setSiteMarchandUrl(normalize(request.getSiteMarchandUrl()));
        dossier.setApplicationMobile(normalize(request.getApplicationMobile()));
        dossier.setCommissionLocaleEcommerce(normalize(request.getCommissionLocaleEcommerce()));
        dossier.setCommissionEtrangereEcommerce(
            normalize(request.getCommissionEtrangereEcommerce())
        );
        dossier.setFraisMiseEnServiceEcommerce(
            normalize(request.getFraisMiseEnServiceEcommerce())
        );
        dossier.setModeleQrSoftpos(normalize(request.getModeleQrSoftpos()));
        dossier.setNombreQrSoftpos(
            parseOptionalInteger(request.getNombreQrSoftpos(), "La quantité SoftPOS / QR Code")
        );
        dossier.setCommissionLocaleQrSoftpos(normalize(request.getCommissionLocaleQrSoftpos()));
        dossier.setCommissionEtrangereQrSoftpos(
            normalize(request.getCommissionEtrangereQrSoftpos())
        );
        dossier.setFraisServiceQrSoftpos(normalize(request.getFraisServiceQrSoftpos()));
        dossier.setConditionsQrSoftpos(normalize(request.getConditionsQrSoftpos()));
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
        return dossier;
    }

    private int saveDocuments(
        AffiliationRegistrationRequest request,
        dossier_affiliation dossier,
        Map<String, MultipartFile> uploadedDocuments
    ) {
        if (uploadedDocuments.isEmpty()) {
            return saveDocumentMetadataOnly(request, dossier);
        }

        int savedDocuments = 0;
        Path dossierDirectory = uploadRoot.resolve("dossier-" + dossier.getIdDossier());

        try {
            Files.createDirectories(dossierDirectory);

            for (Map.Entry<String, MultipartFile> entry : uploadedDocuments.entrySet()) {
                String documentKey = entry.getKey();
                MultipartFile file = entry.getValue();

                if (file == null || file.isEmpty()) {
                    continue;
                }

                documentMimeValidator.validate(file);

                String originalFilename = sanitizeFileName(file.getOriginalFilename());
                String storedFilename =
                    documentKey + "-" + System.currentTimeMillis() + "-" + originalFilename;
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

            return savedDocuments;
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Impossible d'enregistrer les documents du dossier.",
                exception
            );
        }
    }

    private int saveDocumentMetadataOnly(
        AffiliationRegistrationRequest request,
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

    private Set<String> requiredDocumentKeys(String merchantType) {
        TypeCommercant typeCommercant = mapMerchantType(merchantType);
        Set<String> documentKeys = new LinkedHashSet<>();

        switch (typeCommercant) {
            case PERSONNE_PHYSIQUE -> {
                documentKeys.add("cinDocument");
                documentKeys.add("ribDocument");
            }
            case PERSONNE_MORALE -> {
                documentKeys.add("statutsDocument");
                documentKeys.add("rcDocument");
                documentKeys.add("iceDocument");
                documentKeys.add("cinRepresentantDocument");
                documentKeys.add("pvNominationDocument");
                documentKeys.add("ribDocument");
            }
            case AUTO_ENTREPRENEUR -> {
                documentKeys.add("cinDocument");
                documentKeys.add("attestationAeDocument");
                documentKeys.add("ribDocument");
            }
            case ASSOCIATION_FONDATION -> {
                documentKeys.add("cinSignataireDocument");
                documentKeys.add("pvAssociationDocument");
                documentKeys.add("statutsDocument");
                documentKeys.add("listeMembresDocument");
                documentKeys.add("ribDocument");
            }
        }

        return documentKeys;
    }

    private boolean hasProvidedDocument(
        AffiliationRegistrationRequest request,
        Map<String, MultipartFile> uploadedDocuments,
        String documentKey
    ) {
        MultipartFile uploadedFile = uploadedDocuments.get(documentKey);
        if (uploadedFile != null && !uploadedFile.isEmpty()) {
            return true;
        }

        return StringUtils.hasText(documentNameFromRequest(request, documentKey));
    }

    private Iterable<DocumentDefinition> documentDefinitions(String merchantType) {
        TypeCommercant typeCommercant = mapMerchantType(merchantType);

        return switch (typeCommercant) {
            case PERSONNE_PHYSIQUE -> Set.of(
                new DocumentDefinition("cinDocument", TypeDocument.PIECE_IDENTITE),
                new DocumentDefinition("ribDocument", TypeDocument.RIB),
                new DocumentDefinition("patenteDocument", TypeDocument.INSCRIPTION_PATENTE)
            );
            case PERSONNE_MORALE -> Set.of(
                new DocumentDefinition("statutsDocument", TypeDocument.STATUTS_SOCIETE),
                new DocumentDefinition("rcDocument", TypeDocument.REGISTRE_COMMERCE),
                new DocumentDefinition("iceDocument", TypeDocument.ICE),
                new DocumentDefinition("cinRepresentantDocument", TypeDocument.CIN_REPRESENTANT_LEGAL),
                new DocumentDefinition("pvNominationDocument", TypeDocument.PV_NOMINATION),
                new DocumentDefinition("ribDocument", TypeDocument.RIB)
            );
            case AUTO_ENTREPRENEUR -> Set.of(
                new DocumentDefinition("cinDocument", TypeDocument.PIECE_IDENTITE),
                new DocumentDefinition("attestationAeDocument", TypeDocument.ATTESTATION_AUTO_ENTREPRENEUR),
                new DocumentDefinition("ribDocument", TypeDocument.RIB)
            );
            case ASSOCIATION_FONDATION -> Set.of(
                new DocumentDefinition("cinSignataireDocument", TypeDocument.CIN_SIGNATAIRE),
                new DocumentDefinition("pvAssociationDocument", TypeDocument.PV_ASSOCIATION),
                new DocumentDefinition("statutsDocument", TypeDocument.STATUTS_ASSOCIATION),
                new DocumentDefinition("listeMembresDocument", TypeDocument.LISTE_MEMBRES),
                new DocumentDefinition("ribDocument", TypeDocument.RIB)
            );
        };
    }

    private String documentNameFromRequest(AffiliationRegistrationRequest request, String documentKey) {
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
            case "patenteDocument" ->
                mapMerchantType(merchantType) == TypeCommercant.PERSONNE_PHYSIQUE
                    ? TypeDocument.INSCRIPTION_PATENTE
                    : TypeDocument.PATENTE;
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
            default ->
                throw new IllegalArgumentException("Type de document inconnu: " + documentKey);
        };
    }

    private TypeCommercant mapMerchantType(String value) {
        String normalizedValue = normalize(value);

        return switch (Objects.requireNonNullElse(normalizedValue, "").toUpperCase(Locale.ROOT)) {
            case "PP" -> TypeCommercant.PERSONNE_PHYSIQUE;
            case "PM" -> TypeCommercant.PERSONNE_MORALE;
            case "AE" -> TypeCommercant.AUTO_ENTREPRENEUR;
            case "ASSOCIATION" -> TypeCommercant.ASSOCIATION_FONDATION;
            default -> throw new IllegalArgumentException("Type de commerçant invalide.");
        };
    }

    private TypeAffiliation mapAffiliationType(String value) {
        String normalizedValue = normalize(value);

        return switch (Objects.requireNonNullElse(normalizedValue, "").toUpperCase(Locale.ROOT)) {
            case "TPE" -> TypeAffiliation.TPE;
            case "SOFTPOS" -> TypeAffiliation.SOFTPOS;
            case "QRCODE" -> TypeAffiliation.QR_CODE;
            case "ECOMMERCE" -> TypeAffiliation.E_COMMERCE;
            case "ENCAISSEMENTECOMMERCE" -> TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE;
            default -> throw new IllegalArgumentException("Type d'affiliation invalide.");
        };
    }

    private String resolveDisplayName(
        AffiliationRegistrationRequest request,
        TypeCommercant typeCommercant
    ) {
        return switch (typeCommercant) {
            case PERSONNE_PHYSIQUE, AUTO_ENTREPRENEUR -> normalize(
                String.join(
                    " ",
                    Objects.requireNonNullElse(normalize(request.getPrenom()), ""),
                    Objects.requireNonNullElse(normalize(request.getNom()), "")
                ).trim()
            );
            case PERSONNE_MORALE -> normalize(request.getRaisonSociale());
            case ASSOCIATION_FONDATION -> normalize(request.getNomEntite());
        };
    }

    private String resolveLegalName(
        AffiliationRegistrationRequest request,
        TypeCommercant typeCommercant
    ) {
        return switch (typeCommercant) {
            case PERSONNE_MORALE -> normalize(request.getRaisonSociale());
            case ASSOCIATION_FONDATION -> normalize(request.getNomEntite());
            case PERSONNE_PHYSIQUE, AUTO_ENTREPRENEUR ->
                resolveDisplayName(request, typeCommercant);
        };
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private Integer parseInteger(String value, String fieldLabel) {
        try {
            return Integer.valueOf(normalize(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldLabel + " doit etre numerique.");
        }
    }

    private Integer parseOptionalInteger(String value, String fieldLabel) {
        return StringUtils.hasText(value) ? parseInteger(value, fieldLabel) : null;
    }

    private void validateIntegerRange(String value, String fieldLabel, int min, int max) {
        int parsed = parseInteger(value, fieldLabel);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(
                fieldLabel + " doit etre compris entre " + min + " et " + max + "."
            );
        }
    }

    private String normalize(String value) {
        return normalizeText(value);
    }

    private static String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmedValue = value.trim();
        return switch (trimmedValue.toLowerCase(Locale.ROOT)) {
            case "null", "undefined" -> null;
            default -> trimmedValue;
        };
    }

    private String firstNotBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String generateAccountSecret() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }

    private String sanitizeFileName(String originalFilename) {
        String cleanFileName = StringUtils.cleanPath(
            Objects.requireNonNullElse(originalFilename, "document")
        );
        String sanitized = cleanFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "document" : sanitized;
    }

    private record DocumentDefinition(String key, TypeDocument typeDocument) {
    }

    private record PointVenteRequest(
        String nom,
        String adresse,
        String ville,
        String codePostal,
        String quartier,
        String telephone,
        String email,
        Double latitude,
        Double longitude
    ) {
    }
}
