package com.example.demo.repositories;

import com.example.demo.entities.tpe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TpeRepository extends JpaRepository<tpe, Long> {

    boolean existsByNumeroSerie(String numeroSerie);

    Optional<tpe> findByNumeroSerie(String numeroSerie);

    List<tpe> findAllByOrderByIdTPEDesc();

    long countByPdv_Commercant_IdCommercant(Long commercantId);

    long countByPdv_Commercant_IdCommercantAndStatut(Long commercantId, String statut);

    long countByPdv_IdPDV(Long pdvId);

    long countByPdv_SousCommercant_Utilisateur_Id(Long utilisateurId);

    List<tpe> findTop8ByPdv_Commercant_IdCommercantOrderByIdTPEDesc(Long commercantId);

    List<tpe> findTop8ByPdv_SousCommercant_Utilisateur_IdOrderByIdTPEDesc(Long utilisateurId);

    @Query(
        """
        select t
        from tpe t
        where t.pdv.commercant.idCommercant = :commercantId
        order by t.idTPE desc
        """
    )
    List<tpe> findByCommercantIdOrderByIdDesc(@Param("commercantId") Long commercantId);

    @Query(
        """
        select t
        from tpe t
        where t.pdv.sousCommercant.utilisateur.id = :utilisateurId
        order by t.idTPE desc
        """
    )
    List<tpe> findBySubMerchantUserIdOrderByIdDesc(@Param("utilisateurId") Long utilisateurId);

    @Query(
        """
        select t
        from tpe t
        where t.idTPE = :tpeId
          and t.pdv.commercant.idCommercant = :commercantId
        """
    )
    Optional<tpe> findAssignedToCommercant(
        @Param("tpeId") Long tpeId,
        @Param("commercantId") Long commercantId
    );
}
