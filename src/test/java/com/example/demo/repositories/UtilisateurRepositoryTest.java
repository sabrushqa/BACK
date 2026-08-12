package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie la recherche insensible a la casse par e-mail (utilisee par le
 * login) et existsByEmailIgnoreCase (utilisee pour detecter les doublons).
 */
@SpringBootTest
@Transactional
class UtilisateurRepositoryTest {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private utilisateur persistUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    @Test
    void prePersistAppliesDefaultsWhenFieldsAreLeftUnset() {
        // Tous les autres tests fixent explicitement dateCreation/active/tokenVersion
        // avant la sauvegarde: la branche par defaut de @PrePersist n'etait donc
        // jamais exercee. Ici on ne renseigne rien de tout ca.
        utilisateur user = new utilisateur();
        user.setEmail("prepersist.defaults@test.lanacash.ma");
        user.setRole(RoleUser.COMMERCANT);

        utilisateur saved = utilisateurRepository.save(user);

        assertThat(saved.getDateCreation()).isEqualTo(LocalDate.now());
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getTokenVersion()).isZero();
    }

    @Test
    void findByEmailIgnoreCaseMatchesRegardlessOfCase() {
        persistUser("Jane.Doe@Test.LanaCash.ma");

        assertThat(utilisateurRepository.findByEmailIgnoreCase("jane.doe@test.lanacash.ma")).isPresent();
        assertThat(utilisateurRepository.findByEmailIgnoreCase("JANE.DOE@TEST.LANACASH.MA")).isPresent();
        assertThat(utilisateurRepository.findByEmailIgnoreCase("autre@test.lanacash.ma")).isEmpty();
    }

    @Test
    void existsByEmailIgnoreCaseDetectsDuplicateRegardlessOfCase() {
        persistUser("doublon@test.lanacash.ma");

        assertThat(utilisateurRepository.existsByEmailIgnoreCase("DOUBLON@test.lanacash.ma")).isTrue();
        assertThat(utilisateurRepository.existsByEmailIgnoreCase("inexistant@test.lanacash.ma")).isFalse();
    }
}
