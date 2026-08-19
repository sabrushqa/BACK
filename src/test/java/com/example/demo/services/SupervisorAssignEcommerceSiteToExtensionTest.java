package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.dto.SupervisorEcommerceSiteAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meme verification que SupervisorAssignTpeToExtensionPdvTest, pour le canal
 * e-commerce : une demande d'extension (NOUVEAU_PDV) de type E_COMMERCE ou
 * ENCAISSEMENT_ET_ECOMMERCE doit pouvoir affecter un site e-commerce
 * exactement comme un dossier d'affiliation initiale — via le vrai chemin de
 * production (requestNewPdvProduct), pas une construction manuelle.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorAssignEcommerceSiteToExtensionTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

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
    void assigningEcommerceSiteToExtensionDossierSucceeds() {
        utilisateur merchantUser = persistUser("commercant.ext-ecom@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(principalDossier);

        // Chemin reel : le commercant deja affilie TPE demande en plus un
        // canal e-commerce (extension).
        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "E_COMMERCE", null, null, null, null, null, null,
                "INTEGRATION_API", "https://nouvelle-boutique.example.ma", null,
                null, null,
                null
            )
        );

        List<dossier_affiliation> dossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant());
        dossier_affiliation extensionDossier = dossiers.stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));

        assertThat(extensionDossier.getSiteMarchandUrl()).isEqualTo("https://nouvelle-boutique.example.ma");

        extensionDossier.setStatus(StatusDossier.ACCEPTE);
        dossierAffiliationRepository.save(extensionDossier);

        utilisateur backOfficeUser = persistUser("boa.ext-ecom@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        when(switchMonetiqueClient.provisionnerSiteEcommerce(any(), any())).thenReturn(
            new SwitchMonetiqueClient.SwitchSiteEcommerce(
                "ECOM-EXT-TEST-1", commercant.getIdCommercant().toString(),
                "https://nouvelle-boutique.example.ma", true
            )
        );

        supervisorManagementService.assignEcommerceSiteToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            new SupervisorEcommerceSiteAssignRequest(extensionDossier.getIdDossier(), null)
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(extensionDossier.getIdDossier())
            .orElseThrow();
        assertThat(reloaded.getIdSiteEcommerceAffecte()).isEqualTo("ECOM-EXT-TEST-1");
    }
}
