package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie la resiliation d'un commercant actif par le superviseur : c'est le
 * seul point d'entree qui produit un vrai label metier "abandonne=1"
 * (statut RESILIE) exploitable pour un futur reentrainement de
 * lana-merchant-intelligence sur donnees reelles.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorResiliationTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

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
    void supervisorCanResilierActiveCommercant() {
        utilisateur superviseur = persistUser("superviseur.resiliation@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur commercantUser = persistUser("commercant.aresilier@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        supervisorManagementService.resilierCommercant(
            "Bearer " + tokenFor(superviseur),
            commercant.getIdCommercant(),
            "Fermeture du commerce."
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossier.getIdDossier()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.RESILIE);
        assertThat(reloaded.getMotifRefus()).isEqualTo("Fermeture du commerce.");

        utilisateur reloadedUser = utilisateurRepository.findById(commercantUser.getId()).orElseThrow();
        assertThat(reloadedUser.getActive()).isFalse();
    }

    @Test
    void cannotResilierCommercantNotYetAccepted() {
        utilisateur superviseur = persistUser("superviseur.resiliation2@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur commercantUser = persistUser("commercant.nonaccepte@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant = commercantRepository.save(commercant);
        final Long commercantId = commercant.getIdCommercant();

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.SOUMIS);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        assertThatThrownBy(() ->
            supervisorManagementService.resilierCommercant("Bearer " + tokenFor(superviseur), commercantId, null)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actif");
    }
}
