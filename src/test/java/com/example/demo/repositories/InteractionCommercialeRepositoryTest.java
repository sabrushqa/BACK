package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.interaction_commerciale;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie le comptage des relances en retard (date <= aujourd'hui, statut
 * different de "TERMINE"), logique combinant negation et comparaison de
 * date - la plus a risque de ce repository.
 */
@SpringBootTest
@Transactional
class InteractionCommercialeRepositoryTest {

    @Autowired
    private InteractionCommercialeRepository interactionCommercialeRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    private commerciale commercialeA;

    @BeforeEach
    void setUp() {
        utilisateur user = new utilisateur();
        user.setEmail("interaction.commerciale@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCIAL);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        user = utilisateurRepository.save(user);

        commercialeA = new commerciale();
        commercialeA.setUtilisateur(user);
        commercialeA = commercialeRepository.save(commercialeA);
    }

    private interaction_commerciale newInteraction(LocalDate prochaineRelance, String statut) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        interaction_commerciale interaction = new interaction_commerciale();
        interaction.setCommerciale(commercialeA);
        interaction.setDossierAffiliation(dossier);
        interaction.setProchaineRelanceDate(prochaineRelance);
        interaction.setStatut(statut);
        interaction.setDateInteraction(LocalDate.now());
        return interactionCommercialeRepository.save(interaction);
    }

    @Test
    void countsOnlyOverdueAndNotCompletedFollowUps() {
        LocalDate today = LocalDate.now();
        newInteraction(today.minusDays(1), "EN_ATTENTE");
        newInteraction(today, "EN_ATTENTE");
        newInteraction(today.minusDays(1), "TERMINE");
        newInteraction(today.plusDays(5), "EN_ATTENTE");

        long overdueCount = interactionCommercialeRepository
            .countByCommerciale_IdCommercialAndProchaineRelanceDateLessThanEqualAndStatutNot(
                commercialeA.getIdCommercial(),
                today,
                "TERMINE"
            );

        assertThat(overdueCount).isEqualTo(2);
    }
}
