package com.example.demo.repositories;

import com.example.demo.entities.back_office;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackOfficeRepository extends JpaRepository<back_office, Long> {

    List<back_office> findAllByOrderByNomAscPrenomAscIdBackOfficeAsc();

    Optional<back_office> findByUtilisateur_Id(Long utilisateurId);
}
