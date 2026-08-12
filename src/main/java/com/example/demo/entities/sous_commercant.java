package com.example.demo.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sous_commercants")
@Getter
@Setter
@NoArgsConstructor
public class sous_commercant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSousCommercant;

    private String nom;

    private String prenom;

    private String telephone;

    private String email;

    private LocalDate dateAffectation;

    private String statut;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", unique = true)
    private utilisateur utilisateur;

    // Direct link used only for e-commerce merchants, which have no PDV for a
    // sous-commerçant to attach to via pdv.sousCommercant (the TPE-side path).
    @ManyToOne
    @JoinColumn(name = "commercant_id")
    private commercant commercant;

    // "SITE_MARCHAND" or "APPLICATION_MOBILE" — which e-commerce channel this
    // sous-commerçant manages. Only set when commercant is populated.
    private String canalEcommerce;
}
