package com.example.demo.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "back_offices")
@Getter
@Setter
@NoArgsConstructor
public class back_office {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idBackOffice;

    private String nom;

    private String prenom;

    private String matricule;

    private String service;

    private Boolean peutValiderDossiers;

    private Boolean peutAffecterTpe;

    private Boolean peutGererReclamations;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", unique = true)
    private utilisateur utilisateur;
}
