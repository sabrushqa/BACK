package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReclamationRequest(
    @NotBlank @Size(max = 20)   String referenceChat,
    @NotBlank @Size(max = 50)   String typeProbleme,
    @NotBlank @Size(max = 3000) String description,
    @NotBlank @Size(max = 10)   String priorite,
                                Long   tpeId,
              @Size(max = 1000) String commentaire,
    // Uniquement utilises par le chatbot (ChatbotReclamationController) :
    // merchantId identifie le commercant côté v2 (jamais fourni par le
    // portail marchand, qui resout deja le commercant via son propre JWT) ;
    // tpeReference est le repli texte quand tpeId ne peut pas etre resolu
    // (TPE Oracle sans ligne locale) — voir entities/Reclamation.java.
                                Long   merchantId,
              @Size(max = 100)  String tpeReference
) {}
