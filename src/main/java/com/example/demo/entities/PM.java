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
@Table(name = "pms")
@Getter
@Setter
@NoArgsConstructor
public class PM {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String raisonSociale;

    private String registreCommerce;

    private String ice;

    private String formeJuridique;

    private String representantLegal;

    @OneToOne
    @JoinColumn(name = "commercant_id", unique = true)
    private commercant commercant;
}
