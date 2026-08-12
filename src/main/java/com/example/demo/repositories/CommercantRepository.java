package com.example.demo.repositories;

import com.example.demo.entities.commercant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercantRepository extends JpaRepository<commercant, Long> {

    Optional<commercant> findByUtilisateur_Id(Long utilisateurId);
}
