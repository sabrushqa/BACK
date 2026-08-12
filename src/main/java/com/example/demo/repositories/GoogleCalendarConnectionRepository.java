package com.example.demo.repositories;

import com.example.demo.entities.google_calendar_connection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarConnectionRepository
    extends JpaRepository<google_calendar_connection, Long> {

    Optional<google_calendar_connection> findByUtilisateur_Id(Long utilisateurId);
}
