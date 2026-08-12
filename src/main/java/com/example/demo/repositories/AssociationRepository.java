package com.example.demo.repositories;

import com.example.demo.entities.Association;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssociationRepository extends JpaRepository<Association, Long> {
    Optional<Association> findByCommercant_IdCommercant(Long commercantId);
}
