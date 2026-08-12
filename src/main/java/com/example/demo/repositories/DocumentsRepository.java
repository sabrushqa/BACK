package com.example.demo.repositories;

import com.example.demo.entities.documents;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentsRepository extends JpaRepository<documents, Long> {

    List<documents> findAllByDossierAffiliation_IdDossierOrderByDateUploadDescIdDocumentDesc(
        Long dossierId
    );

    Optional<documents> findByIdDocumentAndDossierAffiliation_IdDossier(
        Long documentId,
        Long dossierId
    );
}
