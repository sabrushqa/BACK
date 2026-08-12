package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
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
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exerce validateStaffCanAccessDossier (methode privee de
 * StaffAffiliationManagementService partagee par tous les endpoints de
 * consultation de dossier), via getCommercialInteractions. Les tests
 * existants n'authentifiaient que des superviseurs, qui court-circuitent
 * toujours cette validation (return immediat) : les branches BACK_OFFICE et
 * COMMERCIAL n'etaient donc jamais exercees.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffAffiliationAccessControlTest {

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

    private Long persistOrdinaryDossier() {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Access Control");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        return dossier.getIdDossier();
    }

    @Test
    void backOfficeWithoutProfileIsRejected() {
        utilisateur user = persistUser("bo.noprofile.access@test.lanacash.ma", RoleUser.BACK_OFFICE);
        Long dossierId = persistOrdinaryDossier();

        assertThatThrownBy(() -> staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossierId
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Compte back office introuvable");
    }

    @Test
    void backOfficeWithoutValidationPermissionFlagStillHasAccess() {
        // La restriction par permission individuelle (peutValiderDossiers) a ete supprimee :
        // tout agent BACK_OFFICE a acces au dossier, quelle que soit la valeur du flag.
        utilisateur user = persistUser("bo.novalidate.access@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(user);
        backOffice.setPeutValiderDossiers(false);
        backOfficeRepository.save(backOffice);
        Long dossierId = persistOrdinaryDossier();

        var response = staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossierId
        );

        assertThat(response).isNotNull();
    }

    @Test
    void backOfficeWithPermissionCanAccessOrdinaryDossier() {
        utilisateur user = persistUser("bo.allowed.access@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(user);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);
        Long dossierId = persistOrdinaryDossier();

        var response = staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossierId
        );

        assertThat(response.interactions()).isEmpty();
    }

    @Test
    void backOfficeCannotAccessExtensionDossierOutsideItsPerimeter() {
        utilisateur user = persistUser("bo.extension.foreign@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(user);
        backOffice.setPeutValiderDossiers(true);
        backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Extension Foreign");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation extension = new dossier_affiliation();
        extension.setCommercant(commercant);
        extension.setOrigineCreation("NOUVEAU_PDV");
        extension.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        extension.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(extension);

        assertThatThrownBy(() -> staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), extension.getIdDossier()
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("perimetre back office");
    }

    @Test
    void backOfficeCanAccessExtensionDossierDirectlyAssignedToIt() {
        utilisateur user = persistUser("bo.extension.owner@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(user);
        backOffice.setPeutValiderDossiers(true);
        backOffice = backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Extension Owner");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation extension = new dossier_affiliation();
        extension.setCommercant(commercant);
        extension.setOrigineCreation("NOUVEAU_PDV");
        extension.setBackOffice(backOffice);
        extension.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        extension.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(extension);

        var response = staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), extension.getIdDossier()
        );

        assertThat(response.interactions()).isEmpty();
    }

    @Test
    void commercialWithoutProfileIsRejected() {
        utilisateur user = persistUser("com.noprofile.access@test.lanacash.ma", RoleUser.COMMERCIAL);
        Long dossierId = persistOrdinaryDossier();

        assertThatThrownBy(() -> staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossierId
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("Compte commercial introuvable");
    }

    @Test
    void commercialCannotAccessDossierAssignedToAnotherCommercial() {
        utilisateur user = persistUser("com.foreign.access@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(user);
        commercialeRepository.save(commerciale);

        Long dossierId = persistOrdinaryDossier();

        assertThatThrownBy(() -> staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossierId
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("perimetre commercial");
    }

    @Test
    void commercialCanAccessDossierAssignedToItViaCommercialeAssignee() {
        utilisateur user = persistUser("com.assigned.access@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(user);
        commerciale = commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Assigned Access");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommercialeAssignee(commerciale);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        var response = staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossier.getIdDossier()
        );

        assertThat(response.interactions()).isEmpty();
    }

    @Test
    void commercialCanAccessOwnCommercialDirectDossier() {
        utilisateur user = persistUser("com.direct.owner.access@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(user);
        commerciale = commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Direct Owner Access");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setCommerciale(commerciale);
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        var response = staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), dossier.getIdDossier()
        );

        assertThat(response.interactions()).isEmpty();
    }

    @Test
    void commercialCannotAccessCommercialDirectDossierOwnedByAnotherCommercial() {
        utilisateur owner = persistUser("com.direct.realowner@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale ownerCommerciale = new commerciale();
        ownerCommerciale.setUtilisateur(owner);
        ownerCommerciale = commercialeRepository.save(ownerCommerciale);

        utilisateur intruder = persistUser("com.direct.intruder@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale intruderCommerciale = new commerciale();
        intruderCommerciale.setUtilisateur(intruder);
        commercialeRepository.save(intruderCommerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Direct Real Owner");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setCommerciale(ownerCommerciale);
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        assertThatThrownBy(() -> staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(intruder), dossier.getIdDossier()
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("perimetre commercial");
    }

    @Test
    void commercialCannotAccessForeignExtensionDossier() {
        utilisateur user = persistUser("com.extension.foreign@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(user);
        commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Extension Foreign Commercial");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation extension = new dossier_affiliation();
        extension.setCommercant(commercant);
        extension.setOrigineCreation("NOUVEAU_PDV");
        extension.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        extension.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(extension);

        assertThatThrownBy(() -> staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(user), extension.getIdDossier()
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN)
            .hasMessageContaining("perimetre commercial");
    }

    @Test
    void supervisorCanAlwaysAccessAnyDossier() {
        utilisateur superviseur = persistUser("sup.always.access@test.lanacash.ma", RoleUser.SUPERVISEUR);
        Long dossierId = persistOrdinaryDossier();

        var response = staffAffiliationManagementService.getCommercialInteractions(
            "Bearer " + tokenFor(superviseur), dossierId
        );

        assertThat(response.interactions()).isEmpty();
    }
}
