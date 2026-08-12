package com.example.demo.dto;

import java.time.LocalDate;

public record ReclamationResponse(
    Long      idReclamation,
    String    referenceChat,
    String    typeProbleme,
    String    description,
    String    statut,
    String    priorite,
    LocalDate dateCreation,
    LocalDate dateResolution,
    String    commentaire,
    String    tpeNumeroSerie,
    String    tpeModele,
    // Reference TPE lisible quand `tpe` (FK locale) n'a pas pu etre resolue
    // (TPE affecte cote Oracle, sans ligne locale correspondante) — voir
    // entities/Reclamation.java::tpeReference. Repli d'affichage cote UI
    // quand tpeNumeroSerie est absent.
    String    tpeReference
) {}
