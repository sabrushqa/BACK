package com.example.demo.repositories;

import com.example.demo.entities.microsoft_calendar_connection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MicrosoftCalendarConnectionRepository
    extends JpaRepository<microsoft_calendar_connection, Long> {

    Optional<microsoft_calendar_connection> findByUtilisateur_Id(Long utilisateurId);
}
