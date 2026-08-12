package com.example.demo.repositories;

import com.example.demo.entities.dossier_affiliation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DossierAffiliationRepository extends JpaRepository<dossier_affiliation, Long> {

    List<dossier_affiliation> findAllByOrderByDateSoumissionDescIdDossierDesc();

    Optional<dossier_affiliation> findFirstByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
        Long commercantId
    );

    List<dossier_affiliation> findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(
        Long commercantId
    );

    List<dossier_affiliation> findAllByRequestedPdvIsNotNull();
}
