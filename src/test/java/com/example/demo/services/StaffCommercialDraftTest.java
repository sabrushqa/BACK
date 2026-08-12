package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.CommercialAffiliationDraftRequest;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.documents;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DocumentsRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce la creation et la mise a jour d'un brouillon de demande d'affiliation
 * par une commerciale (pas de validation de champs obligatoires en brouillon).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffCommercialDraftTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private DocumentsRepository documentsRepository;

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    private CommercialAffiliationDraftRequest draftRequest(String email) {
        CommercialAffiliationDraftRequest request = new CommercialAffiliationDraftRequest();
        request.setTypeCommercant("PERSONNE_PHYSIQUE");
        request.setTypeAffiliation("TPE");
        request.setEmail(email);
        request.setNom("Chraibi");
        request.setPrenom("Sami");
        request.setCin("EF112233");
        return request;
    }

    @Test
    void createsDraftDossierForNewMerchant() {
        utilisateur commercialUser = persistUser("commercial.draft@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            draftRequest("nouveau.commercant.draft@test.lanacash.ma")
        );

        assertThat(response.dossierId()).isNotNull();
        dossier_affiliation dossier = dossierAffiliationRepository.findById(response.dossierId()).orElseThrow();
        assertThat(dossier.getStatus()).isEqualTo(StatusDossier.BROUILLON);
        assertThat(dossier.getOrigineCreation()).isEqualTo("COMMERCIAL_DIRECT");
    }

    @Test
    void updatesExistingDraft() {
        utilisateur commercialUser = persistUser("commercial.draft2@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        var createResponse = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            draftRequest("brouillon.a.modifier@test.lanacash.ma")
        );

        CommercialAffiliationDraftRequest updated = draftRequest("brouillon.a.modifier@test.lanacash.ma");
        updated.setNom("NomModifie");

        staffAffiliationManagementService.saveCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            createResponse.dossierId(),
            updated
        );

        dossier_affiliation dossier = dossierAffiliationRepository.findById(createResponse.dossierId()).orElseThrow();
        assertThat(dossier.getCommercant().getNomCommercial()).contains("NomModifie");
        assertThat(dossier.getStatus()).isEqualTo(StatusDossier.BROUILLON);
    }

    @Test
    void createsDraftForPersonneMoraleWithPointVentes() {
        utilisateur commercialUser = persistUser("commercial.draft.pm@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        CommercialAffiliationDraftRequest request = new CommercialAffiliationDraftRequest();
        request.setTypeCommercant("PERSONNE_MORALE");
        request.setTypeAffiliation("TPE");
        request.setEmail("nouveau.pm.draft@test.lanacash.ma");
        request.setRaisonSociale("Lana Distribution SARL");
        request.setRc("RC98765");
        request.setIce("ICE555");
        request.setFormeJuridique("SARL");
        request.setRepresentantLegal("Nadia Fassi");
        request.setPointVentesJson(
            "[{\"nom\":\"PDV Centre\",\"adresse\":\"12 rue Hassan II\",\"ville\":\"Casablanca\","
                + "\"codePostal\":\"20000\",\"telephone\":\"0600000001\",\"email\":\"pdv@test.lanacash.ma\"}]"
        );

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            request
        );

        dossier_affiliation dossier = dossierAffiliationRepository.findById(response.dossierId()).orElseThrow();
        assertThat(dossier.getCommercant().getType()).isEqualTo(com.example.demo.enums.TypeCommercant.PERSONNE_MORALE);
    }

    @Test
    void createsDraftForAutoEntrepreneur() {
        utilisateur commercialUser = persistUser("commercial.draft.ae@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        CommercialAffiliationDraftRequest request = new CommercialAffiliationDraftRequest();
        request.setTypeCommercant("AUTO_ENTREPRENEUR");
        request.setTypeAffiliation("TPE");
        request.setEmail("nouveau.ae.draft@test.lanacash.ma");
        request.setNom("Chraibi");
        request.setPrenom("Omar");
        request.setNumeroAutoEntrepreneur("AE-7788");

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            request
        );

        dossier_affiliation dossier = dossierAffiliationRepository.findById(response.dossierId()).orElseThrow();
        assertThat(dossier.getCommercant().getType())
            .isEqualTo(com.example.demo.enums.TypeCommercant.AUTO_ENTREPRENEUR);
    }

    @Test
    void createsDraftForAssociation() {
        utilisateur commercialUser = persistUser("commercial.draft.assoc@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        CommercialAffiliationDraftRequest request = new CommercialAffiliationDraftRequest();
        request.setTypeCommercant("ASSOCIATION_FONDATION");
        request.setTypeAffiliation("TPE");
        request.setEmail("nouveau.assoc.draft@test.lanacash.ma");
        request.setNomEntite("Association Solidarite Lana");
        request.setRepresentantLegal("Samira Tazi");
        request.setObjet("Aide sociale");

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            request
        );

        dossier_affiliation dossier = dossierAffiliationRepository.findById(response.dossierId()).orElseThrow();
        assertThat(dossier.getCommercant().getType())
            .isEqualTo(com.example.demo.enums.TypeCommercant.ASSOCIATION_FONDATION);
    }

    @Test
    void createsDraftWithRealUploadedDocumentFiles() {
        utilisateur commercialUser = persistUser("commercial.draft.realupload@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        CommercialAffiliationDraftRequest request = draftRequest("nouveau.realupload.draft@test.lanacash.ma");

        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile cinFile = new MockMultipartFile("cinDocument", "cin.png", "image/png", pngBytes);
        MockMultipartFile ribFile = new MockMultipartFile("ribDocument", "rib.png", "image/png", pngBytes);

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            request,
            Map.of("cinDocument", cinFile, "ribDocument", ribFile)
        );

        java.util.List<documents> savedDocuments =
            documentsRepository.findAllByDossierAffiliation_IdDossierOrderByDateUploadDescIdDocumentDesc(
                response.dossierId()
            );
        assertThat(savedDocuments).hasSize(2);
        assertThat(savedDocuments)
            .allMatch(document -> document.getTailleFichier() != null && document.getTailleFichier() > 0);
    }

    @Test
    void createsDraftWithEveryKnownDocumentKeyForPersonneMorale() {
        utilisateur commercialUser = persistUser("commercial.draft.alldockeys@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        CommercialAffiliationDraftRequest request = draftRequest("nouveau.alldockeys.draft@test.lanacash.ma");
        request.setTypeCommercant("PERSONNE_MORALE");

        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        String[] documentKeys = {
            "patenteDocument", "statutsDocument", "rcDocument", "iceDocument",
            "cinRepresentantDocument", "pvNominationDocument", "attestationAeDocument",
            "cinSignataireDocument", "pvAssociationDocument", "listeMembresDocument"
        };
        Map<String, org.springframework.web.multipart.MultipartFile> uploadedDocuments = new java.util.LinkedHashMap<>();
        for (String key : documentKeys) {
            uploadedDocuments.put(key, new MockMultipartFile(key, key + ".png", "image/png", pngBytes));
        }

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            request,
            uploadedDocuments
        );

        java.util.List<documents> savedDocuments =
            documentsRepository.findAllByDossierAffiliation_IdDossierOrderByDateUploadDescIdDocumentDesc(
                response.dossierId()
            );
        assertThat(savedDocuments).hasSize(documentKeys.length);
        assertThat(savedDocuments)
            .anyMatch(document -> document.getTypeDocument() == com.example.demo.enums.TypeDocument.STATUTS_SOCIETE);
    }

    @Test
    void createsDraftWithStatutsDocumentMappedToAssociationVariantForAssociations() {
        utilisateur commercialUser = persistUser("commercial.draft.statutsassoc@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commercialeRepository.save(commerciale);

        CommercialAffiliationDraftRequest request = draftRequest("nouveau.statutsassoc.draft@test.lanacash.ma");
        request.setTypeCommercant("ASSOCIATION_FONDATION");

        byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile statutsFile =
            new MockMultipartFile("statutsDocument", "statuts.png", "image/png", pngBytes);

        var response = staffAffiliationManagementService.createCommercialDraft(
            "Bearer " + tokenFor(commercialUser),
            request,
            Map.of("statutsDocument", statutsFile)
        );

        documents saved = documentsRepository.findAllByDossierAffiliation_IdDossierOrderByDateUploadDescIdDocumentDesc(
            response.dossierId()
        ).get(0);
        assertThat(saved.getTypeDocument()).isEqualTo(com.example.demo.enums.TypeDocument.STATUTS_ASSOCIATION);
    }
}
