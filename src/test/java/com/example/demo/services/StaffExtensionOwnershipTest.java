package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.dto.StaffAffiliationOverviewResponse;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Une demande d'extension (NOUVEAU_PDV) n'a pas toujours sa propre commerciale
 * ou son propre back office assignes directement: si aucun n'est renseigne sur
 * le dossier d'extension lui-meme, la propriete doit remonter au dossier
 * principal accepte du meme commercant (isExtensionOwnedByCommercial /
 * isExtensionOwnedByBackOffice). Ce test verifie ce chemin de repli, distinct
 * du cas ou l'extension porte deja sa propre affectation.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffExtensionOwnershipTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

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
    void commercialSeesUnassignedExtensionThroughPrincipalDossierOwnership() {
        utilisateur commercialUser = persistUser("commercial.extension.owner@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Extension Fallback");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setCommerciale(commerciale);
        principalDossier.setDateSoumission(LocalDate.now().minusDays(30));
        dossierAffiliationRepository.save(principalDossier);

        dossier_affiliation extensionDossier = new dossier_affiliation();
        extensionDossier.setCommercant(commercant);
        extensionDossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        extensionDossier.setOrigineCreation("NOUVEAU_PDV");
        extensionDossier.setDateSoumission(LocalDate.now());
        Long extensionDossierId = dossierAffiliationRepository.save(extensionDossier).getIdDossier();

        StaffAffiliationOverviewResponse response = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(commercialUser)
        );

        assertThat(response.requests())
            .anyMatch(item -> item.dossierId().equals(extensionDossierId));
    }

    @Test
    void backOfficeCanReviewUnassignedExtensionThroughPrincipalDossierOwnership() {
        utilisateur backOfficeUser = persistUser("bo.extension.owner@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutValiderDossiers(true);
        backOffice = backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Extension BackOffice Fallback");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setBackOffice(backOffice);
        principalDossier.setDateSoumission(LocalDate.now().minusDays(30));
        dossierAffiliationRepository.save(principalDossier);

        dossier_affiliation extensionDossier = new dossier_affiliation();
        extensionDossier.setCommercant(commercant);
        extensionDossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        extensionDossier.setOrigineCreation("NOUVEAU_PDV");
        extensionDossier.setDateSoumission(LocalDate.now());
        extensionDossier = dossierAffiliationRepository.save(extensionDossier);
        final Long extensionDossierId = extensionDossier.getIdDossier();

        staffAffiliationManagementService.reviewMerchantDossier(
            "Bearer " + tokenFor(backOfficeUser),
            extensionDossierId,
            new AffiliationReviewRequest(
                "REFUSE",
                "Types de problème: DOCUMENTS\nMotif: Documents illisibles"
            )
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(extensionDossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.INCOMPLET);
    }
}
