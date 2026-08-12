package com.example.demo.repositories;

import com.example.demo.entities.sous_commercant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SousCommercantRepository extends JpaRepository<sous_commercant, Long> {

    Optional<sous_commercant> findByUtilisateur_Id(Long utilisateurId);
}
