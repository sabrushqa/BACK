package com.example.demo.repositories;

import com.example.demo.entities.AE;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AERepository extends JpaRepository<AE, Long> {
    Optional<AE> findByCommercant_IdCommercant(Long commercantId);
    java.util.List<AE> findAllByCommercant_IdCommercantIn(java.util.List<Long> commercantIds);

}
