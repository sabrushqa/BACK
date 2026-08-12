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
@Table(name = "tpes")
@Getter
@Setter
@NoArgsConstructor
public class tpe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTPE;

    private String numeroSerie;

    private String modele;

    private String statut;

    private LocalDate dateActivation;

    private String adresseIP;

    private String versionLogiciel;

    private String typeConnexion;

    private String typeCompatible;

    private Boolean actif = true;

    private LocalDate dateAffectationCommerciale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdv_id")
    private pdv pdv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commerciale_id")
    private commerciale commerciale;
}
