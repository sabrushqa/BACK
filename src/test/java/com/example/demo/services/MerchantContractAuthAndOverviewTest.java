package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exerce les branches d'erreur d'authentification de readAuthenticatedMerchant
 * (token expire, role incorrect, compte inactif, session invalidee) et les
 * variantes de mapContractOverview (commerciale assignee avec/sans nom).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantContractAuthAndOverviewTest {

    @Autowired
    private MerchantContractManagementService merchantContractManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private commercant persistCommercantWithDossier(utilisateur user, StatusDossier status) {
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant.setNomCommercial("Boutique Auth Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setStatus(status);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);
        return commercant;
    }

    @Test
    void rejectsExpiredToken() {
        utilisateur user = persistUser("commercant.expired.contrat@test.lanacash.ma", RoleUser.COMMERCANT);
        persistCommercantWithDossier(user, StatusDossier.CONTRAT_A_SIGNER);
        String expiredToken = TestJwtSupport.mintExpiredToken("kc-sub-" + user.getId(), user.getEmail());

        // Nimbus rejette deja le JWT expire au decodage (avant meme d'atteindre
        // isTokenExpired()) : le message reflete donc "invalide", pas "expire".
        assertThatThrownBy(() -> merchantContractManagementService.getLatestContract("Bearer " + expiredToken))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("invalide");
    }

    @Test
    void rejectsSessionInvalidatedAfterTokenIssuance() throws InterruptedException {
        utilisateur user = persistUser("commercant.invalidated.contrat@test.lanacash.ma", RoleUser.COMMERCANT);
        persistCommercantWithDossier(user, StatusDossier.CONTRAT_A_SIGNER);
        String token = TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);

        Thread.sleep(1100);
        user.setTokenVersion(1);
        utilisateurRepository.save(user);

        assertThatThrownBy(() -> merchantContractManagementService.getLatestContract("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("invalidee");
    }

    @Test
    void rejectsNonMerchantRole() {
        utilisateur user = persistUser("superviseur.contrat.auth@test.lanacash.ma", RoleUser.SUPERVISEUR);
        String token = TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);

        assertThatThrownBy(() -> merchantContractManagementService.getLatestContract("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("commerçant");
    }

    @Test
    void rejectsInactiveMerchantAccount() {
        // Le login Keycloak (resolveKeycloakUser -> activateAfterKeycloakLogin)
        // reactive automatiquement un compte inactif tant qu'aucune date de
        // desactivation n'est enregistree: il faut donc en poser une pour que
        // ce test simule reellement un compte desactive.
        utilisateur user = persistUser("commercant.inactive.contrat@test.lanacash.ma", RoleUser.COMMERCANT);
        persistCommercantWithDossier(user, StatusDossier.CONTRAT_A_SIGNER);
        user.setActive(false);
        user.setDateDesactivation(LocalDate.now());
        utilisateurRepository.save(user);
        String token = TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);

        assertThatThrownBy(() -> merchantContractManagementService.getLatestContract("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Activez");
    }

    @Test
    void overviewIncludesAssignedCommercialeDisplayName() {
        utilisateur commercialUser = persistUser("commercial.contrat.overview@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Bennani");
        commerciale.setPrenom("Youssef");
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.overview.commerciale@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant.setNomCommercial("Boutique Overview Commerciale");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setCommerciale(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        var overview = merchantContractManagementService.getLatestContract(
            "Bearer " + TestJwtSupport.mintKeycloakToken("kc-sub-" + merchantUser.getId(), merchantUser.getEmail(), 300)
        );

        assertThat(overview.commercialAttribue()).contains("Youssef").contains("Bennani");
    }

    @Test
    void overviewFallsBackToCommercialeEmailWhenNameIsBlank() {
        utilisateur commercialUser = persistUser("commercial.contrat.emailonly@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.overview.emailonly@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant.setNomCommercial("Boutique Overview Email Only");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setRib("007123456789012345678901");
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setCommerciale(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        var overview = merchantContractManagementService.getLatestContract(
            "Bearer " + TestJwtSupport.mintKeycloakToken("kc-sub-" + merchantUser.getId(), merchantUser.getEmail(), 300)
        );

        assertThat(overview.commercialAttribue()).isEqualTo("commercial.contrat.emailonly@test.lanacash.ma");
    }

    @Test
    void overviewLeavesCommercialBlankWhenNoneAssigned() {
        utilisateur merchantUser = persistUser("commercant.overview.nocommercial@test.lanacash.ma", RoleUser.COMMERCANT);
        persistCommercantWithDossier(merchantUser, StatusDossier.CONTRAT_A_SIGNER);

        var overview = merchantContractManagementService.getLatestContract(
            "Bearer " + TestJwtSupport.mintKeycloakToken("kc-sub-" + merchantUser.getId(), merchantUser.getEmail(), 300)
        );

        assertThat(overview.commercialAttribue()).isEmpty();
    }

    @Test
    void uploadSignedContractRejectsWhenDossierStatusDoesNotAllowUpload() throws Exception {
        utilisateur merchantUser = persistUser("commercant.upload.wrongstatus@test.lanacash.ma", RoleUser.COMMERCANT);
        persistCommercantWithDossier(merchantUser, StatusDossier.EN_ATTENTE_VALIDATION_BOA);

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "contrat.pdf", "application/pdf", "irrelevant".getBytes()
        );

        assertThatThrownBy(() -> merchantContractManagementService.uploadSignedContract(
            "Bearer " + TestJwtSupport.mintKeycloakToken("kc-sub-" + merchantUser.getId(), merchantUser.getEmail(), 300),
            file
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("après generation du contrat");
    }
}
