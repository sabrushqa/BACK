package com.example.demo.repositories;

import com.example.demo.entities.PM;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PMRepository extends JpaRepository<PM, Long> {
    Optional<PM> findByCommercant_IdCommercant(Long commercantId);
    java.util.List<PM> findAllByCommercant_IdCommercantIn(java.util.List<Long> commercantIds);

}
