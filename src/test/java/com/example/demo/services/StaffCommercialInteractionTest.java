package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.CommercialInteractionRequest;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
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
 * Exerce l'ajout d'une interaction commerciale (appel/relance/visite) sur un
 * dossier cree directement par la commerciale, et verifie la mise a jour du
 * statut de prospection du dossier.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffCommercialInteractionTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

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
    void addsInteractionAndUpdatesProspectStatus() {
        utilisateur commercialUser = persistUser("commercial.interaction@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Interaction Test");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setCommerciale(commerciale);
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        var response = staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL",
                "Prospect interesse",
                "A rappeler la semaine prochaine",
                "FAIT",
                LocalDate.now().toString(),
                null,
                null,
                null
            )
        );

        assertThat(response.interactions()).hasSize(1);

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.CONTACTE);
    }

    private dossier_affiliation newDirectDossier(commerciale commerciale) {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Interaction Test " + System.nanoTime());
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setCommerciale(commerciale);
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        return dossierAffiliationRepository.save(dossier);
    }

    @Test
    void acceptsExplicitCompatibleProspectStatus() {
        utilisateur commercialUser = persistUser("commercial.interaction.explicit@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL", "Prospect abandonne", "Ne repond plus", "FAIT",
                LocalDate.now().toString(), null, null, "ABANDONNE"
            )
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.ABANDONNE);
    }

    @Test
    void rejectsUnknownProspectStatusValue() {
        utilisateur commercialUser = persistUser("commercial.interaction.badstatus@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            staffAffiliationManagementService.addCommercialInteraction(
                "Bearer " + tokenFor(commercialUser),
                dossierId,
                new CommercialInteractionRequest(
                    "APPEL", "test", "test", "FAIT",
                    LocalDate.now().toString(), null, null, "STATUT_INEXISTANT"
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsProspectStatusIncompatibleWithInteractionType() {
        utilisateur commercialUser = persistUser("commercial.interaction.incompatible@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        // RDV_PLANIFIE n'est pas compatible avec le type d'interaction APPEL.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            staffAffiliationManagementService.addCommercialInteraction(
                "Bearer " + tokenFor(commercialUser),
                dossierId,
                new CommercialInteractionRequest(
                    "APPEL", "test", "test", "FAIT",
                    LocalDate.now().toString(), null, null, "RDV_PLANIFIE"
                )
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rdvInteractionWithoutExplicitStatusDefaultsToRdvPlanifie() {
        utilisateur commercialUser = persistUser("commercial.interaction.rdv@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new CommercialInteractionRequest(
                "RDV", "Rendez-vous fixe", null, "FAIT",
                LocalDate.now().toString(), null, null, null
            )
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.RDV_PLANIFIE);
    }

    @Test
    void visiteInteractionWithReminderDateDefaultsToARelancer() {
        utilisateur commercialUser = persistUser("commercial.interaction.visite@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new CommercialInteractionRequest(
                "VISITE", "Visite effectuee", null, "FAIT",
                LocalDate.now().toString(), LocalDate.now().plusDays(7).toString(), "RELANCE", null
            )
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.A_RELANCER);
    }

    @Test
    void visiteInteractionWithoutReminderDateDefaultsToEnNegociation() {
        utilisateur commercialUser = persistUser("commercial.interaction.visitesansrelance@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new CommercialInteractionRequest(
                "VISITE", "Visite effectuee", null, "FAIT",
                LocalDate.now().toString(), null, null, null
            )
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.EN_NEGOCIATION);
    }

    @Test
    void relanceInteractionDefaultsToARelancer() {
        utilisateur commercialUser = persistUser("commercial.interaction.relance@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = newDirectDossier(commerciale);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new CommercialInteractionRequest(
                "RELANCE", "Relance effectuee", null, "FAIT",
                LocalDate.now().toString(), LocalDate.now().plusDays(3).toString(), "APPEL", null
            )
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.A_RELANCER);
    }
}
