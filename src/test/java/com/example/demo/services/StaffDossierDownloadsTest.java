package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.documents;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDocument;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeDocument;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DocumentsRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce le telechargement d'un contrat genere reellement sur disque, et le
 * dossier complet fusionne (chemin heureux sans documents deposes).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffDossierDownloadsTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private DocumentsRepository documentsRepository;

    @Autowired
    private com.example.demo.repositories.PdvRepository pdvRepository;

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

    @Test
    void downloadsGeneratedContractForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.download.contrat@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Download Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setGeneratedContractPath(generated.cheminStocke());
        dossier.setGeneratedContractFileName(generated.nomFichier());
        dossier = dossierAffiliationRepository.save(dossier);

        var download = staffAffiliationManagementService.downloadContratGenere(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier()
        );

        assertThat(download.content()).isNotEmpty();
        assertThat(download.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadsFullDossierMergedPdfWithoutAnyDepositedDocuments() {
        utilisateur superviseur = persistUser("superviseur.download.full@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Full Download Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        var download = staffAffiliationManagementService.downloadFullDossier(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier()
        );

        assertThat(download.content()).isNotEmpty();
        assertThat(download.fileName()).contains("complet");
    }

    @Test
    void downloadsSignedContractForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.download.signed@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Signed Download Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setSignedContractPath(generated.cheminStocke());
        dossier.setSignedContractFileName(generated.nomFichier());
        dossier = dossierAffiliationRepository.save(dossier);

        var download = staffAffiliationManagementService.downloadSignedContract(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier()
        );

        assertThat(download.content()).isNotEmpty();
        assertThat(download.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void downloadsUploadedDocumentForSupervisor() {
        utilisateur superviseur = persistUser("superviseur.download.document@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Document Download Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        // Reutilise un fichier reellement present sur disque (contrat genere) comme
        // stand-in pour un document depose: seul le chemin physique importe pour
        // exercer resolveUploadedDocumentPath()/isExistingFile() en succes reel.
        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);

        documents document = new documents();
        document.setDossierAffiliation(dossier);
        document.setTypeDocument(TypeDocument.RIB);
        document.setCheminStockage(generated.cheminStocke());
        document.setTailleFichier(1L);
        document.setDateUpload(LocalDate.now());
        document.setStatutDocument(StatusDocument.UPLOADE);
        document = documentsRepository.save(document);

        var download = staffAffiliationManagementService.downloadDocument(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier(),
            document.getIdDocument()
        );

        assertThat(download.content()).isNotEmpty();
        assertThat(download.fileName()).isNotBlank();
    }

    @Test
    void downloadsFullDossierWithARealDepositedDocumentMergedIn() {
        utilisateur superviseur = persistUser("superviseur.download.fulldocs@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Full Download With Docs Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);

        documents document = new documents();
        document.setDossierAffiliation(dossier);
        document.setTypeDocument(TypeDocument.RIB);
        document.setCheminStockage(generated.cheminStocke());
        document.setTailleFichier(1L);
        document.setDateUpload(LocalDate.now());
        document.setStatutDocument(StatusDocument.UPLOADE);
        documentsRepository.save(document);

        var download = staffAffiliationManagementService.downloadFullDossier(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier()
        );

        assertThat(download.content()).isNotEmpty();
        assertThat(download.fileName()).contains("complet");
    }

    @Test
    void fullDossierIncludesTheContractOfAnApprovedExtensionRequest() {
        utilisateur superviseur = persistUser("superviseur.download.withextension@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Extension Download Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossierPrincipal = new dossier_affiliation();
        dossierPrincipal.setCommercant(commercant);
        dossierPrincipal.setTypeAffiliation(TypeAffiliation.TPE);
        dossierPrincipal.setRib("007123456789012345678901");
        dossierPrincipal.setDateSoumission(LocalDate.now());
        dossierPrincipal = dossierAffiliationRepository.save(dossierPrincipal);

        var downloadSansExtension = staffAffiliationManagementService.downloadFullDossier(
            "Bearer " + tokenFor(superviseur),
            dossierPrincipal.getIdDossier()
        );

        pdv nouveauPdv = new pdv();
        nouveauPdv.setCommercant(commercant);
        nouveauPdv.setNomPDV("Nouveau PDV Extension");
        nouveauPdv.setStatut("ACTIF");
        nouveauPdv = pdvRepository.save(nouveauPdv);

        dossier_affiliation extension = new dossier_affiliation();
        extension.setCommercant(commercant);
        extension.setTypeAffiliation(TypeAffiliation.TPE);
        extension.setOrigineCreation("NOUVEAU_PDV");
        extension.setStatus(StatusDossier.ACCEPTE);
        extension.setRequestedPdv(nouveauPdv);
        extension.setDateSoumission(LocalDate.now());
        extension = dossierAffiliationRepository.save(extension);

        ServiceDocumentContratAffiliation.ContratGenere extensionContract =
            serviceDocumentContratAffiliation.genererContrat(extension);
        extension.setGeneratedContractPath(extensionContract.cheminStocke());
        extension.setGeneratedContractFileName(extensionContract.nomFichier());
        dossierAffiliationRepository.save(extension);

        var downloadAvecExtension = staffAffiliationManagementService.downloadFullDossier(
            "Bearer " + tokenFor(superviseur),
            dossierPrincipal.getIdDossier()
        );

        assertThat(downloadAvecExtension.content()).isNotEmpty();
        // Le contrat de l'extension approuvee est fusionne en plus du dossier
        // principal : le PDF resultant est forcement plus volumineux.
        assertThat(downloadAvecExtension.content().length).isGreaterThan(downloadSansExtension.content().length);
    }

    @Test
    void fullDossierIncludesTheSignedContractOnTopOfTheGeneratedOne() {
        utilisateur superviseur = persistUser("superviseur.download.withsigned@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Signed In Full Download Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        var downloadSansSignature = staffAffiliationManagementService.downloadFullDossier(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier()
        );

        ServiceDocumentContratAffiliation.ContratGenere signed =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setSignedContractPath(signed.cheminStocke());
        dossier.setSignedContractFileName(signed.nomFichier());
        dossierAffiliationRepository.save(dossier);

        var downloadAvecSignature = staffAffiliationManagementService.downloadFullDossier(
            "Bearer " + tokenFor(superviseur),
            dossier.getIdDossier()
        );

        assertThat(downloadAvecSignature.content()).isNotEmpty();
        assertThat(downloadAvecSignature.content().length).isGreaterThan(downloadSansSignature.content().length);
    }
}
