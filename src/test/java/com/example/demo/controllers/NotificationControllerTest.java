package com.example.demo.controllers;

import static org.springframework.test.web.servlet.assertj.MockMvcTester.create;

import com.example.demo.entities.notifications;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie que les notifications sont cloisonnees par utilisateur et que
 * markAllAsRead ne touche que les notifications de l'utilisateur authentifie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestJwtSupport.class)
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    private MockMvcTester mvc;
    private utilisateur userA;
    private utilisateur userB;

    @BeforeEach
    void setUp() {
        mvc = create(mockMvc);

        userA = persistUser("notif.controller.a@test.lanacash.ma");
        userB = persistUser("notif.controller.b@test.lanacash.ma");

        newNotification(userA, false);
        newNotification(userA, false);
        newNotification(userB, false);
    }

    private utilisateur persistUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private notifications newNotification(utilisateur owner, boolean lue) {
        notifications n = new notifications();
        n.setUtilisateur(owner);
        n.setDateEnvoi(LocalDate.now());
        n.setStatutLecture(lue);
        n.setMessage("Test notification");
        n.setTypeNotification(TypeNotification.DOSSIER_A_VALIDER_BOA);
        return notificationsRepository.save(n);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void returnsOnlyCurrentUserNotificationsWithCorrectUnreadCount() {
        mvc.perform(
            MockMvcRequestBuilders.get("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(userA))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK)
            .bodyJson()
            .extractingPath("$.unreadCount")
            .isEqualTo(2);
    }

    @Test
    void markAllAsReadOnlyAffectsAuthenticatedUsersNotifications() {
        mvc.perform(
            MockMvcRequestBuilders.post("/api/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(userA))
        )
            .assertThat()
            .hasStatus(HttpStatus.OK);

        boolean userBNotificationStillUnread = notificationsRepository
            .findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(userB.getId())
            .stream()
            .anyMatch(n -> !Boolean.TRUE.equals(n.getStatutLecture()));

        org.assertj.core.api.Assertions.assertThat(userBNotificationStillUnread).isTrue();
    }

    @Test
    void rejectsRequestWithoutAuthentication() {
        mvc.perform(MockMvcRequestBuilders.get("/api/notifications"))
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
