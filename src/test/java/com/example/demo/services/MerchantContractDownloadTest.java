package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce le telechargement du contrat genere reellement et la verification
 * de signature (chemin heureux) pour le commercant authentifie.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantContractDownloadTest {

    @Autowired
    private MerchantContractManagementService merchantContractManagementService;

    @Autowired
    private ServiceDocumentContratAffiliation serviceDocumentContratAffiliation;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    private utilisateur persistUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void downloadsLatestGeneratedContractForAuthenticatedMerchant() {
        utilisateur merchantUser = persistUser("commercant.download.contrat@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        ServiceDocumentContratAffiliation.ContratGenere generated =
            serviceDocumentContratAffiliation.genererContrat(dossier);
        dossier.setGeneratedContractPath(generated.cheminStocke());
        dossier.setGeneratedContractFileName(generated.nomFichier());
        dossierAffiliationRepository.save(dossier);

        var download = merchantContractManagementService.downloadLatestContratGenere(
            "Bearer " + tokenFor(merchantUser)
        );

        assertThat(download.contenu()).isNotEmpty();
        assertThat(download.typeContenu()).isEqualTo("application/pdf");
    }

    @Test
    void verifySignatureRejectsNonLanaCashFile() {
        MockMultipartFile unrelated = new MockMultipartFile(
            "file", "document.pdf", "application/pdf", "not-a-real-contract".getBytes()
        );

        var response = merchantContractManagementService.verifySignature(unrelated);

        assertThat(response.signed()).isFalse();
    }
}
