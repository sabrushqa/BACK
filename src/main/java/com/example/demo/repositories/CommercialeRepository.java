package com.example.demo.repositories;

import com.example.demo.entities.commerciale;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialeRepository extends JpaRepository<commerciale, Long> {

    List<commerciale> findAllByOrderByNomAscPrenomAscIdCommercialAsc();

    Optional<commerciale> findByUtilisateur_Id(Long utilisateurId);
}
