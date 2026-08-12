package com.example.demo.entities;

import jakarta.persistence.Entity;
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
@Table(name = "pdvs")
@Getter
@Setter
@NoArgsConstructor
public class pdv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPDV;

    private String nomPDV;

    private String adresse;

    private String ville;

    private String codePostal;

    private String quartier;

    private Double latitude;

    private Double longitude;

    private String telephone;

    private String email;

    private String statut;

    private LocalDate dateCreation;

    @ManyToOne
    @JoinColumn(name = "commercant_id")
    private commercant commercant;

    @ManyToOne
    @JoinColumn(name = "sous_commercant_id")
    private sous_commercant sousCommercant;
}
