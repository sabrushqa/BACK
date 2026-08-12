package com.example.demo.repositories;

import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PdvRepository extends JpaRepository<pdv, Long> {

    long countByCommercant_IdCommercant(Long commercantId);

    long countBySousCommercant_Utilisateur_Id(Long utilisateurId);

    List<pdv> findByCommercant_IdCommercantOrderByIdPDVAsc(Long commercantId);

    List<pdv> findTop8ByCommercant_IdCommercantOrderByIdPDVDesc(Long commercantId);

    List<pdv> findTop8BySousCommercant_Utilisateur_IdOrderByIdPDVDesc(Long utilisateurId);

    @Modifying(clearAutomatically = true)
    @Query(
        """
        update pdv p
        set p.statut = :statut
        where p.commercant.idCommercant = :commercantId
        """
    )
    int updateStatutByCommercantId(
        @Param("commercantId") Long commercantId,
        @Param("statut") String statut
    );

    @Query(
        """
        select distinct p.sousCommercant
        from pdv p
        where p.commercant.idCommercant = :commercantId
          and p.sousCommercant is not null
        """
    )
    List<sous_commercant> findDistinctSousCommercantsByCommercantId(
        @Param("commercantId") Long commercantId
    );
}
