package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.ReclamationRequest;
import com.example.demo.entities.Reclamation;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.ReclamationRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce la creation de reclamation par un commercant, les statistiques et
 * le tableau de bord back-office, et le changement de statut (avec date de
 * resolution automatique).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class ReclamationServiceExtraTest {

    @Autowired
    private ReclamationService reclamationService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private ReclamationRepository reclamationRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private com.example.demo.repositories.PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

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
    void createsReclamationForAuthenticatedMerchant() {
        utilisateur merchantUser = persistUser("commercant.reclamation.create@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercantRepository.save(commercant);

        var response = reclamationService.createReclamation(
            "Bearer " + tokenFor(merchantUser),
            new ReclamationRequest("CHAT-001", "CONNECTIVITE", "Le TPE ne se connecte plus", "HAUTE", null, "Urgent", null, null)
        );

        assertThat(response.idReclamation()).isNotNull();
        assertThat(response.statut()).isEqualTo("EN_ATTENTE");
    }

    @Test
    void computesStatsAndDashboardForBackOffice() {
        utilisateur superviseur = persistUser("superviseur.reclamation.stats@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Stats Test");
        commercant = commercantRepository.save(commercant);

        Reclamation reclamation = new Reclamation();
        reclamation.setCommercant(commercant);
        reclamation.setStatut("EN_ATTENTE");
        reclamation.setPriorite("HAUTE");
        reclamation.setTypeProbleme("CONNECTIVITE");
        reclamation.setDateCreation(LocalDate.now());
        reclamationRepository.save(reclamation);

        var stats = reclamationService.getStats("Bearer " + tokenFor(superviseur));
        assertThat(stats.get("total")).isGreaterThanOrEqualTo(1L);

        var dashboard = reclamationService.getDashboard("Bearer " + tokenFor(superviseur), 7, null);
        assertThat(dashboard.parJour()).isNotEmpty();
    }

    @Test
    void updateStatutSetsResolutionDateWhenResolved() {
        utilisateur superviseur = persistUser("superviseur.reclamation.update@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Update Test");
        commercant = commercantRepository.save(commercant);

        Reclamation reclamation = new Reclamation();
        reclamation.setCommercant(commercant);
        reclamation.setStatut("EN_COURS");
        reclamation.setPriorite("HAUTE");
        reclamation.setTypeProbleme("CONNECTIVITE");
        reclamation.setDateCreation(LocalDate.now());
        reclamation = reclamationRepository.save(reclamation);

        var response = reclamationService.updateStatut(
            "Bearer " + tokenFor(superviseur),
            reclamation.getIdReclamation(),
            "RESOLU"
        );

        assertThat(response.statut()).isEqualTo("RESOLU");
        assertThat(response.dateResolution()).isEqualTo(LocalDate.now());
    }

    @Test
    void dashboardListsOverdueReclamationsOlderThanThreeDays() {
        utilisateur superviseur = persistUser("superviseur.reclamation.overdue@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Overdue Test");
        commercant = commercantRepository.save(commercant);

        Reclamation overdue = new Reclamation();
        overdue.setCommercant(commercant);
        overdue.setStatut("EN_COURS");
        overdue.setPriorite("HAUTE");
        overdue.setTypeProbleme("CONNECTIVITE");
        overdue.setDateCreation(LocalDate.now().minusDays(10));
        reclamationRepository.save(overdue);

        var dashboard = reclamationService.getDashboard("Bearer " + tokenFor(superviseur), 30, null);

        assertThat(dashboard.enRetard()).isNotEmpty();
        assertThat(dashboard.enRetardCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void updateStatutAssignsResolvingBackOfficeWhenAuthenticatedAsBackOffice() {
        utilisateur backOfficeUser = persistUser("bo.reclamation.resolve@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutGererReclamations(true);
        backOffice = backOfficeRepository.save(backOffice);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Update BackOffice Test");
        commercant = commercantRepository.save(commercant);

        Reclamation reclamation = new Reclamation();
        reclamation.setCommercant(commercant);
        reclamation.setStatut("EN_COURS");
        reclamation.setPriorite("HAUTE");
        reclamation.setTypeProbleme("CONNECTIVITE");
        reclamation.setDateCreation(LocalDate.now());
        reclamation = reclamationRepository.save(reclamation);

        var response = reclamationService.updateStatut(
            "Bearer " + tokenFor(backOfficeUser),
            reclamation.getIdReclamation(),
            "ESCALADE"
        );

        assertThat(response.statut()).isEqualTo("ESCALADE");
        Reclamation reloaded = reclamationRepository.findById(reclamation.getIdReclamation()).orElseThrow();
        assertThat(reloaded.getBackOffice().getIdBackOffice()).isEqualTo(backOffice.getIdBackOffice());
    }

    @Test
    void rejectsDashboardAccessForBackOfficeWithoutProfile() {
        utilisateur backOfficeUser = persistUser("bo.reclamation.noprofile@test.lanacash.ma", RoleUser.BACK_OFFICE);

        assertThatThrownBy(() -> reclamationService.getDashboard("Bearer " + tokenFor(backOfficeUser), 7, null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.FORBIDDEN)
            .hasMessageContaining("back office introuvable");
    }

    @Test
    void allowsDashboardAccessForBackOfficeWithoutPermissionFlag() {
        // La restriction par permission individuelle (peutGererReclamations) a ete supprimee :
        // tout agent BACK_OFFICE ayant un compte back office valide accede au dashboard.
        utilisateur backOfficeUser = persistUser("bo.reclamation.nopermission@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutGererReclamations(false);
        backOfficeRepository.save(backOffice);

        var dashboard = reclamationService.getDashboard("Bearer " + tokenFor(backOfficeUser), 7, null);

        assertThat(dashboard).isNotNull();
    }

    @Test
    void rejectsCommercialRoleFromAccessingDashboard() {
        utilisateur commercialUser = persistUser("commercial.reclamation.dashboard@test.lanacash.ma", RoleUser.COMMERCIAL);

        assertThatThrownBy(() -> reclamationService.getDashboard("Bearer " + tokenFor(commercialUser), 7, null))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void dashboardFiltersByTypeAndClampsDaysRange() {
        utilisateur superviseur = persistUser("superviseur.reclamation.filtertype@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Filter Type Test");
        commercant = commercantRepository.save(commercant);

        Reclamation matching = new Reclamation();
        matching.setCommercant(commercant);
        matching.setStatut("EN_ATTENTE");
        matching.setPriorite("HAUTE");
        matching.setTypeProbleme("CONNECTIVITE");
        matching.setDateCreation(LocalDate.now());
        reclamationRepository.save(matching);

        Reclamation nonMatching = new Reclamation();
        nonMatching.setCommercant(commercant);
        nonMatching.setStatut("EN_ATTENTE");
        nonMatching.setPriorite("HAUTE");
        nonMatching.setTypeProbleme("TRANSACTION");
        nonMatching.setDateCreation(LocalDate.now());
        reclamationRepository.save(nonMatching);

        // days=200 doit etre plafonne a 90, days<1 (0 ou negatif) retomberait a 7
        var dashboard = reclamationService.getDashboard(
            "Bearer " + tokenFor(superviseur), 200, "CONNECTIVITE"
        );

        assertThat(dashboard.parJour()).hasSize(90);
        assertThat(dashboard.parEtat().get("EN_ATTENTE")).isEqualTo(1L);

        var defaultRangeDashboard = reclamationService.getDashboard(
            "Bearer " + tokenFor(superviseur), 0, null
        );
        assertThat(defaultRangeDashboard.parJour()).hasSize(7);
    }

    @Test
    void createReclamationNormalizesAlternateTypeAndPrioritySpellings() {
        utilisateur merchantUser = persistUser("commercant.reclamation.normalize@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercantRepository.save(commercant);

        String authHeader = "Bearer " + tokenFor(merchantUser);

        assertThat(
            reclamationService.createReclamation(
                authHeader,
                new ReclamationRequest("CHAT-A", "network", "Reseau instable", "CRITICAL", null, null, null, null)
            ).typeProbleme()
        ).isEqualTo("CONNECTIVITE");
        assertThat(
            reclamationService.createReclamation(
                authHeader,
                new ReclamationRequest("CHAT-B", "transaction", "Transaction refusee", "LOW", null, null, null, null)
            ).priorite()
        ).isEqualTo("BASSE");
        assertThat(
            reclamationService.createReclamation(
                authHeader,
                new ReclamationRequest("CHAT-C", "hardware", "TPE casse", "unknown-priority", null, null, null, null)
            ).priorite()
        ).isEqualTo("MOYENNE");
        assertThat(
            reclamationService.createReclamation(
                authHeader,
                new ReclamationRequest("CHAT-D", "software", "Bug ecran", "HIGH", null, null, null, null)
            ).typeProbleme()
        ).isEqualTo("LOGICIEL");
        assertThat(
            reclamationService.createReclamation(
                authHeader,
                new ReclamationRequest("CHAT-E", "type-inconnu", "Autre probleme", "MOYENNE", null, null, null, null)
            ).typeProbleme()
        ).isEqualTo("AUTRE");
    }

    @Test
    void createReclamationAttachesAssignedTpe() {
        utilisateur merchantUser = persistUser("commercant.reclamation.tpe@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        com.example.demo.entities.pdv pointVente = new com.example.demo.entities.pdv();
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        tpe terminal = new tpe();
        terminal.setNumeroSerie("TPE-RECLAMATION-1");
        terminal.setPdv(pointVente);
        terminal = tpeRepository.save(terminal);

        var response = reclamationService.createReclamation(
            "Bearer " + tokenFor(merchantUser),
            new ReclamationRequest("CHAT-TPE", "CONNECTIVITE", "Le TPE plante", "HAUTE", terminal.getIdTPE(), null, null, null)
        );

        assertThat(response.tpeNumeroSerie()).isEqualTo("TPE-RECLAMATION-1");
    }

    @Test
    void getAllReclamationsFiltersByStatutPrioriteAndType() {
        utilisateur superviseur = persistUser("superviseur.reclamation.filters@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Filtres Test");
        commercant = commercantRepository.save(commercant);

        Reclamation reclamation = new Reclamation();
        reclamation.setCommercant(commercant);
        reclamation.setStatut("ESCALADE");
        reclamation.setPriorite("CRITIQUE");
        reclamation.setTypeProbleme("MATERIEL");
        reclamation.setDateCreation(LocalDate.now());
        reclamationRepository.save(reclamation);

        String authHeader = "Bearer " + tokenFor(superviseur);

        assertThat(reclamationService.getAllReclamations(authHeader, "ESCALADE", null, null))
            .anyMatch(item -> item.statut().equals("ESCALADE"));
        assertThat(reclamationService.getAllReclamations(authHeader, null, "CRITIQUE", null))
            .anyMatch(item -> item.priorite().equals("CRITIQUE"));
        assertThat(reclamationService.getAllReclamations(authHeader, null, null, "MATERIEL"))
            .anyMatch(item -> item.typeProbleme().equals("MATERIEL"));
    }
}
