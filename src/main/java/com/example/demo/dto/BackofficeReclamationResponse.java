package com.example.demo.dto;

import java.time.LocalDate;

public record BackofficeReclamationResponse(
    Long      idReclamation,
    String    referenceChat,
    String    typeProbleme,
    String    description,
    String    statut,
    String    priorite,
    LocalDate dateCreation,
    LocalDate dateResolution,
    String    commentaire,
    Long      tpeId,
    String    tpeNumeroSerie,
    String    tpeModele,
    // Repli quand tpeId/tpeNumeroSerie sont absents (TPE Oracle sans ligne
    // locale correspondante) — voir entities/Reclamation.java::tpeReference.
    String    tpeReference,
    Long      commercantId,
    String    commercantNom,
    String    region,
    String    typeAffiliation,
    String    backOfficeTraitant,
    Long      backOfficeId,
    Long      backOfficeUtilisateurId,
    Long      dureeTraitementJours
) {}
