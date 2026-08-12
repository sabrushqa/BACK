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
@Table(name = "commerciales")
@Getter
@Setter
@NoArgsConstructor
public class commerciale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCommercial;

    private String nom;

    private String prenom;

    private String matricule;

    private String region;

    private String telephone;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", unique = true)
    private utilisateur utilisateur;
}
