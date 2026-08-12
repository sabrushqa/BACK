package com.example.demo.entities;

import com.example.demo.enums.TypeNotification;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class notifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idNotification;

    private String message;

    private LocalDate dateEnvoi;

    private Boolean statutLecture;

    @Enumerated(EnumType.STRING)
    private TypeNotification typeNotification;

    private Long dossierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private utilisateur utilisateur;
}
