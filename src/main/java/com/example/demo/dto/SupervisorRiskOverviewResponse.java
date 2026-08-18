package com.example.demo.dto;

import java.util.List;

/**
 * Reponse de la page superviseur "Risque d'abandon" — score IA
 * (lana-merchant-intelligence) par commercant, agrege par secteur, et
 * analyse du taux de refus par canal (TPE vs e-commerce) par secteur.
 */
public record SupervisorRiskOverviewResponse(
    int commercantsAnalyses,
    int commercantsIgnores,
    double scoreMoyen,
    int nombreRisqueEleve,
    int nombreRisqueMoyen,
    int nombreRisqueFaible,
    List<MerchantRiskItem> commercants,
    List<SectorRiskItem> secteursRisque,
    List<SectorCanalItem> canalPerformance,
    List<SectorTpeUsageItem> usageTpeParSecteur,
    // true si switch-monetique-service etait injoignable pendant le calcul :
    // les chiffres ci-dessus sont alors partiels (voire tous a zero), pas
    // "il n'y a pas de risque". Sans ce signal, un superviseur ne peut pas
    // distinguer "aucun commercant a risque" de "la source de donnees etait
    // en panne" — les deux produisent la meme page vide.
    boolean donneesTransactionnellesIndisponibles
) {

    public record MerchantRiskItem(
        Long commercantId,
        String nom,
        String secteur,
        String region,
        String typeAffiliation,
        double scoreRisque,
        String niveauRisque,
        List<String> raisons,
        String actionRecommandee
    ) {
    }

    public record SectorRiskItem(
        String secteur,
        int nombreCommercants,
        double scoreMoyen,
        int nombreRisqueEleve
    ) {
    }

    public record SectorCanalItem(
        String secteur,
        String canal,
        int nombreTransactions,
        double tauxRefus
    ) {
    }

    /**
     * Intensite d'usage du TPE par secteur — independant du score IA :
     * combien de transactions en moyenne par terminal affecte. Un secteur en
     * bas de liste (peu de transactions par TPE) est une cible pour de
     * l'accompagnement/formation ou une offre adaptee ; un secteur en haut de
     * liste est un candidat a une offre de fidelisation/upsell.
     */
    public record SectorTpeUsageItem(
        String secteur,
        int nombreTpeActifs,
        int transactionsTpe,
        double transactionsParTpe
    ) {
    }
}
