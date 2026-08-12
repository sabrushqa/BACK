package com.example.demo.entities;

import com.example.demo.enums.TypeInteraction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "interactions_commerciales")
@Getter
@Setter
@NoArgsConstructor
public class interaction_commerciale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idInteraction;

    @Enumerated(EnumType.STRING)
    private TypeInteraction typeInteraction;

    private LocalDate dateInteraction;

    private String resultat;

    private String commentaire;

    private String statut;

    private LocalDate prochaineRelanceDate;

    @Enumerated(EnumType.STRING)
    private TypeInteraction prochaineRelanceType;

    private String prospectStatus;

    @Column(name = "google_calendar_event_id", length = 1024)
    private String googleCalendarEventId;

    @Column(name = "google_calendar_event_url", length = 2048)
    private String googleCalendarEventUrl;

    @Column(name = "microsoft_calendar_event_id", length = 1024)
    private String microsoftCalendarEventId;

    @Column(name = "microsoft_calendar_event_url", length = 2048)
    private String microsoftCalendarEventUrl;

    @ManyToOne
    @JoinColumn(name = "commerciale_id")
    private commerciale commerciale;

    @ManyToOne
    @JoinColumn(name = "dossier_id")
    private dossier_affiliation dossierAffiliation;
}
