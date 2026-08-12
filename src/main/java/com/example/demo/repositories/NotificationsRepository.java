package com.example.demo.repositories;

import com.example.demo.entities.notifications;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationsRepository extends JpaRepository<notifications, Long> {

    List<notifications> findTop20ByUtilisateur_IdOrderByDateEnvoiDescIdNotificationDesc(
        Long utilisateurId
    );

    List<notifications> findTop20ByUtilisateur_IdAndDateEnvoiGreaterThanEqualOrderByDateEnvoiDescIdNotificationDesc(
        Long utilisateurId,
        LocalDate since
    );

    long countByUtilisateur_IdAndStatutLectureFalseAndDateEnvoiGreaterThanEqual(
        Long utilisateurId,
        LocalDate since
    );
}
