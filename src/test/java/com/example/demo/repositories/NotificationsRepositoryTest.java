package com.example.demo.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.notifications;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie le comptage des notifications non lues avec seuil de date et le
 * cloisonnement par utilisateur, contre SQL Server reel.
 */
@SpringBootTest
@Transactional
class NotificationsRepositoryTest {

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private utilisateur userA;
    private utilisateur userB;

    @BeforeEach
    void setUp() {
        userA = persistUser("notif.a@test.lanacash.ma");
        userB = persistUser("notif.b@test.lanacash.ma");
    }

    private utilisateur persistUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private notifications newNotification(utilisateur owner, LocalDate dateEnvoi, boolean lue) {
        notifications n = new notifications();
        n.setUtilisateur(owner);
        n.setDateEnvoi(dateEnvoi);
        n.setStatutLecture(lue);
        n.setMessage("Test");
        return notificationsRepository.save(n);
    }

    @Test
    void countsOnlyUnreadNotificationsSinceThresholdForTargetUser() {
        LocalDate today = LocalDate.now();
        newNotification(userA, today, false);
        newNotification(userA, today, false);
        newNotification(userA, today, true);
        newNotification(userA, today.minusDays(10), false);
        newNotification(userB, today, false);

        long unreadCount = notificationsRepository
            .countByUtilisateur_IdAndStatutLectureFalseAndDateEnvoiGreaterThanEqual(
                userA.getId(),
                today.minusDays(1)
            );

        assertThat(unreadCount).isEqualTo(2);
    }

    @Test
    void top20OrdersByDateEnvoiDescendingAndScopesToOwner() {
        newNotification(userA, LocalDate.now().minusDays(2), false);
        notifications mostRecent = newNotification(userA, LocalDate.now(), false);
        newNotification(userB, LocalDate.now(), false);

        List<notifications> results = notificationsRepository
            .findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(userA.getId());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getIdNotification()).isEqualTo(mostRecent.getIdNotification());
    }
}
