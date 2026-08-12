package com.example.demo.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReclamationDashboardResponse(
    List<DayCount>       parJour,
    Map<String, Long>    parEtat,
    long                 enRetardCount,
    List<OverdueItem>    enRetard
) {

    public record DayCount(
        LocalDate date,
        long count,
        long enAttente,
        long enCours,
        long resolu,
        long escalade
    ) {}

    public record OverdueItem(
        Long      idReclamation,
        String    referenceChat,
        String    typeProbleme,
        String    statut,
        String    commercantNom,
        LocalDate dateCreation,
        long      joursEnAttente
    ) {}
}
