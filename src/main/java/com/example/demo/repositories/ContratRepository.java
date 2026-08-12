package com.example.demo.repositories;

import com.example.demo.entities.contrat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContratRepository extends JpaRepository<contrat, Long> {

    /** Tous les contrats d'un dossier, du plus récent au plus ancien. */
    List<contrat> findAllByDossierAffiliation_IdDossierOrderByDateGenerationDescIdContratDesc(
        Long dossierId
    );

    /** Dernier contrat actif d'un dossier (le plus récent généré). */
    java.util.Optional<contrat> findFirstByDossierAffiliation_IdDossierOrderByIdContratDesc(
        Long dossierId
    );
}
