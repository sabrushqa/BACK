package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record MerchantSessionResponse(
    Long utilisateurId,
    Long commercantId,
    String email,
    String nom,
    String role,
    boolean active,
    String dossierStatus,
    boolean workspaceUnlocked,
    String dossierMotifRefus,
    String typeCommercant,
    String typeAffiliation,
    String message,
    String accessToken,
    String tokenType,
    OffsetDateTime tokenExpiresAt,
    Summary summary,
    Profile profile,
    List<SubMerchantItem> sousCommercants,
    List<PdvItem> pdvs,
    List<TpeItem> tpes,
    List<TransactionItem> transactions,
    Boolean peutValiderDossiers,
    Boolean peutAffecterTpe,
    Boolean peutGererReclamations
) {

    public record Summary(
        long totalTransactions,
        long totalPdvs,
        long totalTpes,
        long totalSousCommercants
    ) {
    }

    public record Profile(
        String nom,
        String email,
        String telephone,
        String ville,
        String region,
        String activite,
        String typeCommercant,
        String typeAffiliation,
        String siteMarchandUrl,
        String applicationMobile,
        String modeServiceEcommerce
    ) {
    }

    public record SubMerchantItem(
        Long id,
        String nom,
        String prenom,
        String email,
        String telephone,
        String statut,
        Boolean active,
        Long pdvId,
        String pdv,
        String canalEcommerce
    ) {
    }

    public record PdvItem(
        Long id,
        String nom,
        String ville,
        String adresse,
        String telephone,
        String statut,
        String email,
        String codePostal,
        String dateCreation,
        Long sousCommercantId,
        String sousCommercant,
        String sousCommercantEmail,
        String sousCommercantStatut,
        Boolean sousCommercantActive
    ) {
    }

    public record TpeItem(
        String id,
        String numeroSerie,
        String modele,
        String statut,
        String typeConnexion,
        Long pdvId,
        String pdv
    ) {
    }

    public record TransactionItem(
        Long id,
        String dateTransaction,
        String heureTransaction,
        BigDecimal montant,
        String devise,
        String statut,
        String typePaiement,
        String tpe,
        Long pdvId,
        String pdv
    ) {
    }
}
