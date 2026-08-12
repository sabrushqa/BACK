package com.example.demo.repositories;

import com.example.demo.entities.PP;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PPRepository extends JpaRepository<PP, Long> {
    Optional<PP> findByCommercant_IdCommercant(Long commercantId);
}
