package com.example.demo.dto;

public record CreateCommercialeRequest(
    String nom,
    String prenom,
    String email,
    String matricule,
    String region,
    String telephone
) {
}
