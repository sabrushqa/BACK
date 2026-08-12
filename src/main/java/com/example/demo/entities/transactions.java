package com.example.demo.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class transactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTransaction;

    private LocalDate dateTransaction;

    private LocalTime heureTransaction;

    private BigDecimal montant;

    private String devise;

    private String typePaiement;

    private String statutTransaction;

    private String nCarteMasquee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tpe_id")
    private tpe tpe;
}
