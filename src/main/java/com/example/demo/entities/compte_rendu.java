package com.example.demo.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Enregistre une visite terrain du commercial chez le prospect/commerçant.
 * Un dossier d'affiliation peut avoir plusieurs compte-rendus (N visites).
 * Remplace les 27 champs compteRendu* précédemment stockés à plat dans dossier_affiliation.
 */
@Entity
@Table(name = "comptes_rendus")
@Getter
@Setter
@NoArgsConstructor
public class compte_rendu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCompteRendu;

    /** Dossier d'affiliation auquel se rattache cette visite (N compte-rendus par dossier). */
    @ManyToOne
    @JoinColumn(name = "dossier_id", nullable = false)
    private dossier_affiliation dossier;

    /** Commercial qui a effectué la visite. */
    @ManyToOne
    @JoinColumn(name = "commerciale_id")
    private commerciale commerciale;

    /** Date à laquelle ce compte-rendu a été créé dans le système. */
    private LocalDate dateCreation;

    // ─── Qualification du prospect ───────────────────────────────────────────

    /** Qualification du prospect : AFFILIE, PROSPECT, etc. */
    private String qualification;

    /** Nom de l'acquéreur bancaire si le commerçant est déjà affilié. */
    private String acquereur;

    /** Origine du prospect (COMMERCIAL_DIRECT, PROSPECTION, REFERRAL…). */
    private String origineProspect;

    /** Précision sur l'origine du prospect. */
    private String origineProspectDetail;

    // ─── Contact ─────────────────────────────────────────────────────────────

    private String contactNomPrenom;
    private String contactFonction;

    // ─── Point de vente ──────────────────────────────────────────────────────

    private String pointVenteAcronyme;
    private String actionnaires;
    private String nomCommercant;
    private String chaine;
    private String relationsLc;
    private String dateOuverture;
    private String nombreEmployes;
    private String activite;
    private String mcc;
    private String standingMagasin;
    private String natureMarchandises;
    private String superficieLocal;
    private String statutLocal;

    // ─── Données financières ─────────────────────────────────────────────────

    private String chiffreAffairesAnnuel;
    private String partPaiementCarte;
    private String partCarteLocale;

    // ─── Appréciation ────────────────────────────────────────────────────────

    private String profilCommercant;
    private String appreciationVisite;
    private String commentaire;

    // ─── Signature ───────────────────────────────────────────────────────────

    private String faitA;
    private String dateVisite;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) {
            dateCreation = LocalDate.now();
        }
    }
}
