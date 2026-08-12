package com.example.demo.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "reclamations")
@Getter
@Setter
@NoArgsConstructor
public class Reclamation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idReclamation;

    private LocalDate dateCreation;

    private String typeProbleme;

    private String description;

    private String statut;

    private String priorite;

    private LocalDate dateResolution;

    private String commentaire;

    private String referenceChat;

    // Reference TPE lisible (ex: "TPE-000123"), utilisee quand `tpe` (FK)
    // ne peut pas etre resolue : la majorite des TPE reellement affectes
    // (flux BOA principal, SupervisorManagementService::assignTpeToCommercant)
    // vivent cote Oracle (switch-monetique-service) sans ligne correspondante
    // dans cette table locale — `tpe` reste alors null, et sans ce champ il
    // etait impossible de retrouver "tous les tickets du TPE X" autrement
    // qu'en texte libre dans `commentaire`.
    private String tpeReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tpe_id")
    private tpe tpe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercant_id")
    private commercant commercant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "back_office_id")
    private back_office backOffice;
}
