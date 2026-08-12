package com.example.demo.entities;

import com.example.demo.enums.StatusContrat;
import com.example.demo.enums.TypeContrat;
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

/**
 * Représente un contrat d'affiliation généré pour un dossier.
 * Un dossier peut avoir plusieurs contrats (un par PDV affilié ou par relance).
 * Contient également les chemins des documents PDF (contrat généré, rapport
 * commercial, contrat signé) précédemment stockés à plat dans dossier_affiliation.
 */
@Entity
@Table(name = "contrats")
@Getter
@Setter
@NoArgsConstructor
public class contrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idContrat;

    /**
     * Dossier d'affiliation auquel ce contrat appartient.
     * N contrats par dossier (1 par PDV ou par relance d'activation).
     */
    @ManyToOne
    @JoinColumn(name = "dossier_id", nullable = false)
    private dossier_affiliation dossierAffiliation;

    /**
     * Point de vente pour lequel ce contrat est émis (optionnel pour le
     * dossier principal avant création des PDV).
     */
    @ManyToOne
    @JoinColumn(name = "pdv_id")
    private pdv pdv;

    @Enumerated(EnumType.STRING)
    private TypeContrat typeContrat;

    @Enumerated(EnumType.STRING)
    private StatusContrat statutContrat;

    // ─── Dates de cycle de vie ────────────────────────────────────────────────

    private LocalDate dateGeneration;
    private LocalDate dateSignature;
    private LocalDate dateArchivage;

    // ─── Contrat généré (PDF modèle rempli) ──────────────────────────────────

    private String generatedContractPath;
    private String generatedContractFileName;
    private LocalDate generatedContractAt;

    // ─── Rapport commercial (compte-rendu PDF) ────────────────────────────────

    private String commercialReportPath;
    private String commercialReportFileName;
    private LocalDate commercialReportGeneratedAt;

    // ─── Contrat signé (upload par le commerçant) ─────────────────────────────

    private String signedContractPath;
    private String signedContractFileName;
    private LocalDate signedContractUploadedAt;

    /**
     * Chemin générique du fichier contrat final archivé (champ legacy conservé).
     */
    private String fichierContrat;
}
