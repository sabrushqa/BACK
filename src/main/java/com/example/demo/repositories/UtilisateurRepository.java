package com.example.demo.repositories;

import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilisateurRepository extends JpaRepository<utilisateur, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<utilisateur> findByEmailIgnoreCase(String email);

    Optional<utilisateur> findByLoginOtpChallengeId(String challengeId);

    Optional<utilisateur> findByKeycloakId(String keycloakId);

    Optional<utilisateur> findFirstByRole(RoleUser role);

    List<utilisateur> findAllByRole(RoleUser role);
}
