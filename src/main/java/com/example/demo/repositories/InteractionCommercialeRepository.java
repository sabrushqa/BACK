package com.example.demo.repositories;

import com.example.demo.entities.interaction_commerciale;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionCommercialeRepository extends JpaRepository<interaction_commerciale, Long> {

    List<interaction_commerciale> findByDossierAffiliation_IdDossierOrderByDateInteractionDescIdInteractionDesc(
        Long dossierId
    );

    List<interaction_commerciale> findTop1ByDossierAffiliation_IdDossierOrderByDateInteractionDescIdInteractionDesc(
        Long dossierId
    );

    List<interaction_commerciale> findTop1ByDossierAffiliation_IdDossierAndProchaineRelanceDateIsNotNullOrderByProchaineRelanceDateAscIdInteractionDesc(
        Long dossierId
    );

    long countByCommerciale_IdCommercialAndProchaineRelanceDateLessThanEqualAndStatutNot(
        Long commercialeId,
        LocalDate date,
        String statut
    );
}
