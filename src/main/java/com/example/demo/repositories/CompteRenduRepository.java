package com.example.demo.repositories;

import com.example.demo.entities.compte_rendu;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompteRenduRepository extends JpaRepository<compte_rendu, Long> {

    /** Tous les compte-rendus d'un dossier, du plus récent au plus ancien. */
    List<compte_rendu> findAllByDossier_IdDossierOrderByDateCreationDescIdCompteRenduDesc(
        Long dossierId
    );

    /** Dernier compte-rendu d'un dossier. */
    Optional<compte_rendu> findFirstByDossier_IdDossierOrderByIdCompteRenduDesc(
        Long dossierId
    );

    /** Tous les compte-rendus rédigés par un commercial. */
    List<compte_rendu> findAllByCommerciale_IdCommercialOrderByDateCreationDesc(
        Long commercialeId
    );
}
