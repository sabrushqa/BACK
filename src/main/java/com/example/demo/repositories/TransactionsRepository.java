package com.example.demo.repositories;

import com.example.demo.entities.transactions;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionsRepository extends JpaRepository<transactions, Long> {

    long countByTpe_Pdv_Commercant_IdCommercant(Long commercantId);

    long countByTpe_Pdv_SousCommercant_Utilisateur_Id(Long utilisateurId);

    List<transactions> findTop8ByTpe_Pdv_Commercant_IdCommercantOrderByDateTransactionDescHeureTransactionDesc(
        Long commercantId
    );

    List<transactions> findTop8ByTpe_Pdv_SousCommercant_Utilisateur_IdOrderByDateTransactionDescHeureTransactionDesc(
        Long utilisateurId
    );
}
