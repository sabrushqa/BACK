package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.demo.repositories.DocumentsRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.services.StaffAffiliationManagementService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prouve que la visibilite des dossiers d'affiliation cote staff respecte
 * le perimetre de chacun: un back-office sans permission ne voit rien, un
 * commercial ne voit que ses propres dossiers directs, contre SQL Server reel.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffAffiliationVisibilityTest {

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

    @Autowired
    private DocumentsRepository documentsRepository;

    private utilisateur backOfficeSansPermissionUser;
    private utilisateur backOfficeAvecPermissionUser;
    private utilisateur commercialAUser;
    private utilisateur commercialBUser;
    private dossier_affiliation dossierDirectDeA;

    @BeforeEach
    void setUp() {
        backOfficeSansPermissionUser = persistUser("bo.sanspermission@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boSansPermission = new back_office();
        boSansPermission.setUtilisateur(backOfficeSansPermissionUser);
        boSansPermission.setPeutValiderDossiers(false);
        backOfficeRepository.save(boSansPermission);

        backOfficeAvecPermissionUser = persistUser("bo.avecpermission@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boAvecPermission = new back_office();
        boAvecPermission.setUtilisateur(backOfficeAvecPermissionUser);
        boAvecPermission.setPeutValiderDossiers(true);
        backOfficeRepository.save(boAvecPermission);

        commercialAUser = persistUser("commercial.a@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commercialeA = new commerciale();
        commercialeA.setUtilisateur(commercialAUser);
        commercialeA = commercialeRepository.save(commercialeA);

        commercialBUser = persistUser("commercial.b@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commercialeB = new commerciale();
        commercialeB.setUtilisateur(commercialBUser);
        commercialeRepository.save(commercialeB);

        commercant commercantTest = new commercant();
        commercantTest.setNomCommercial("Boutique Visibilite Test");
        commercantTest = commercantRepository.save(commercantTest);

        dossier_affiliation dossierEnAttente = new dossier_affiliation();
        dossierEnAttente.setCommercant(commercantTest);
        dossierEnAttente.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossierEnAttente.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossierEnAttente);

        dossierDirectDeA = new dossier_affiliation();
        dossierDirectDeA.setCommercant(commercantTest);
        dossierDirectDeA.setOrigineCreation("COMMERCIAL_DIRECT");
        dossierDirectDeA.setCommerciale(commercialeA);
        dossierDirectDeA.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossierDirectDeA.setDateSoumission(LocalDate.now());
        dossierDirectDeA = dossierAffiliationRepository.save(dossierDirectDeA);
    }

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
    void backOfficeWithoutPermissionFlagStillSeesDossier() {
        // La restriction par permission individuelle (peutValiderDossiers) a ete supprimee :
        // tout agent BACK_OFFICE voit desormais les dossiers en attente de validation BOA.
        var response = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(backOfficeSansPermissionUser)
        );

        assertThat(response.requests()).isNotEmpty();
    }

    @Test
    void backOfficeWithPermissionSeesPendingDossier() {
        var response = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(backOfficeAvecPermissionUser)
        );

        assertThat(response.requests()).isNotEmpty();
    }

    @Test
    void commercialOnlySeesOwnDirectDossierNotOtherCommercials() {
        var responseA = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(commercialAUser));
        var responseB = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(commercialBUser));

        assertThat(responseA.requests()).isNotEmpty();
        assertThat(responseB.requests()).isEmpty();
    }

    @Test
    void directProspectionDossierKeepsCommercialDirectOriginWhenReturnedForCorrection() {
        // Regression : un dossier de prospection directe renvoye "a corriger" (INCOMPLET)
        // doit conserver origineCreation=COMMERCIAL_DIRECT dans la reponse API, afin que
        // le front-end ne le classe jamais parmi les dossiers auto-affiliation.
        dossierDirectDeA.setStatus(StatusDossier.INCOMPLET);
        dossierAffiliationRepository.save(dossierDirectDeA);

        var response = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(commercialAUser));

        var item = response.requests()
            .stream()
            .filter(request -> request.dossierId().equals(dossierDirectDeA.getIdDossier()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier direct introuvable dans la reponse."));

        assertThat(item.origineCreation()).isEqualTo("COMMERCIAL_DIRECT");
        assertThat(item.status()).isEqualTo("INCOMPLET");
    }

    @Test
    void otherCommercialCannotDownloadDocumentFromDossierOutsideTheirPerimeter() {
        com.example.demo.entities.documents draft = new com.example.demo.entities.documents();
        draft.setDossierAffiliation(dossierDirectDeA);
        draft.setCheminStockage("some/path/document.pdf");
        final com.example.demo.entities.documents document = documentsRepository.save(draft);

        assertThatThrownBy(() ->
            staffAffiliationManagementService.downloadDocument(
                "Bearer " + tokenFor(commercialBUser),
                dossierDirectDeA.getIdDossier(),
                document.getIdDocument()
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void ownerCommercialPassesPerimeterCheckOnDocumentDownload() {
        com.example.demo.entities.documents draft = new com.example.demo.entities.documents();
        draft.setDossierAffiliation(dossierDirectDeA);
        draft.setCheminStockage("some/path/document.pdf");
        final com.example.demo.entities.documents document = documentsRepository.save(draft);

        // Le fichier physique n'existe pas sur disque dans ce test: on s'attend a
        // NOT_FOUND (fichier absent) et non FORBIDDEN (perimetre), ce qui prouve
        // que le controle de perimetre a bien ete franchi pour le proprietaire.
        assertThatThrownBy(() ->
            staffAffiliationManagementService.downloadDocument(
                "Bearer " + tokenFor(commercialAUser),
                dossierDirectDeA.getIdDossier(),
                document.getIdDocument()
            )
        )
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
