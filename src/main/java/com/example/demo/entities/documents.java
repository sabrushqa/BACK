package com.example.demo.entities;

import com.example.demo.enums.StatusDocument;
import com.example.demo.enums.TypeDocument;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class documents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDocument;

    @Enumerated(EnumType.STRING)
    private TypeDocument typeDocument;

    private String cheminStockage;

    private Long tailleFichier;

    private LocalDate dateUpload;

    @Enumerated(EnumType.STRING)
    private StatusDocument statutDocument;

    private String commentaireVerification;

    @ManyToOne
    @JoinColumn(name = "dossier_id")
    private dossier_affiliation dossierAffiliation;
}
