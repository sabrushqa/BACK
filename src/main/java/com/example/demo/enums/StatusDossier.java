package com.example.demo.enums;

public enum StatusDossier {
    BROUILLON,
    EN_ATTENTE_ASSIGNATION,
    SOUMIS,
    EN_ATTENTE_VALIDATION_BOA,
    CONTRAT_A_SIGNER,
    INCOMPLET,
    ACCEPTE,
    ABANDONNE,
    // Commercant deja affilie/actif dont le contrat est resilie apres coup —
    // distinct de ABANDONNE (qui couvre l'abandon PENDANT l'onboarding, avant
    // signature). C'est le vrai label metier "abandonne=1" attendu par
    // lana-merchant-intelligence pour un futur reentrainement sur donnees
    // reelles (voir son README : "contrat resilie, compte desactive, ou
    // absence de transaction pendant 90 jours confirmee comme un depart").
    RESILIE
}
