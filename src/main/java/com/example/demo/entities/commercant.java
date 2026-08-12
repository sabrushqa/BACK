package com.example.demo.entities;

import com.example.demo.enums.TypeCommercant;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commercants")
@Getter
@Setter
@NoArgsConstructor
public class commercant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCommercant;

    private String nomCommercial;

    private String raisonSociale;

    private String registreCommerce;

    private String identifiantFiscal;

    @Enumerated(EnumType.STRING)
    private TypeCommercant type;

    private String adresse;

    private String ville;

    private String region;

    private String telephone;

    private String telephoneSecondaire;

    private String emailContact;

    private String activite;

    private String secteur;

    private String mcc;

    private String chainePointVente;

    private String patente;

    private String fonction;

    private String beneficiairesEffectifs;

    private String dateNaissance;

    private String nationalite;

    private Integer nombrePointsVente;

    private LocalDate dateAffiliation;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", unique = true)
    private utilisateur utilisateur;
}
