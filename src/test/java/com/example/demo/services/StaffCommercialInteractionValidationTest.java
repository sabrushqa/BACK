package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.dto.CommercialInteractionRequest;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
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
 * Exerce les regles metier de validateCommercialInteractionBusinessRules
 * (methode privee appelee par addCommercialInteraction) qui n'etaient pas
 * exercees: date future, resultat vide, statut invalide, incoherence
 * type/date de relance, relance dans le passe, abandon sans motif, converti
 * avec relance, et relance immediate.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffCommercialInteractionValidationTest {

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

    private Long persistCommercialDirectDossier(commerciale commerciale) {
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Interaction Validation");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setCommerciale(commerciale);
        dossier.setStatus(StatusDossier.BROUILLON);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        return dossier.getIdDossier();
    }

    private commerciale persistCommercialUser(String email) {
        utilisateur commercialUser = persistUser(email, RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        return commercialeRepository.save(commerciale);
    }

    @Test
    void rejectsInteractionDateInTheFuture() {
        commerciale commerciale = persistCommercialUser("com.interaction.futuredate@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL", "Interesse", "RAS", "FAIT",
                LocalDate.now().plusDays(1).toString(), null, null, null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futur");
    }

    @Test
    void rejectsBlankResult() {
        commerciale commerciale = persistCommercialUser("com.interaction.blankresult@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest("APPEL", "", "RAS", "FAIT", null, null, null, null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("résultat");
    }

    @Test
    void rejectsInvalidActionStatus() {
        commerciale commerciale = persistCommercialUser("com.interaction.invalidstatut@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest("APPEL", "Interesse", "RAS", "INCONNU", null, null, null, null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FAIT, PLANIFIE ou ANNULE");
    }

    @Test
    void rejectsNextInteractionTypeWithoutReminderDate() {
        commerciale commerciale = persistCommercialUser("com.interaction.typewithoutdate@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL", "Interesse", "RAS", "FAIT", null, null, "APPEL", null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("date de prochaine relance est obligatoire si un type");
    }

    @Test
    void rejectsReminderDateWithoutNextInteractionType() {
        commerciale commerciale = persistCommercialUser("com.interaction.datewithouttype@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL", "Interesse", "RAS", "FAIT", null,
                LocalDate.now().plusDays(3).toString(), null, null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("type de prochaine relance est obligatoire");
    }

    @Test
    void rejectsReminderDateInThePast() {
        commerciale commerciale = persistCommercialUser("com.interaction.pastreminder@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL", "Interesse", "RAS", "FAIT", null,
                LocalDate.now().minusDays(1).toString(), "APPEL", null
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ne peut pas être dans le passé");
    }

    @Test
    void rejectsAbandonedProspectWithoutComment() {
        commerciale commerciale = persistCommercialUser("com.interaction.abandonnocomment@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "APPEL", "Pas interesse", "", "FAIT", null, null, null, "ABANDONNE"
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("abandonné");
    }

    @Test
    void rejectsConvertedProspectStatusAsIncompatibleWithInteraction() {
        commerciale commerciale = persistCommercialUser("com.interaction.convertiwithreminder@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "VISITE", "Signe", "RAS", "FAIT", null,
                LocalDate.now().plusDays(2).toString(), "APPEL", "CONVERTI"
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ne correspond pas au type d'interaction");
    }

    @Test
    void rejectsRelanceWithReminderDateEqualToInteractionDate() {
        commerciale commerciale = persistCommercialUser("com.interaction.relancesamedate@test.lanacash.ma");
        Long dossierId = persistCommercialDirectDossier(commerciale);
        String today = LocalDate.now().toString();

        assertThatThrownBy(() -> staffAffiliationManagementService.addCommercialInteraction(
            "Bearer " + tokenFor(commerciale.getUtilisateur()),
            dossierId,
            new CommercialInteractionRequest(
                "RELANCE", "Rappele", "RAS", "FAIT", today, today, "APPEL", "A_RELANCER"
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("postérieure à la relance effectuée");
    }
}
