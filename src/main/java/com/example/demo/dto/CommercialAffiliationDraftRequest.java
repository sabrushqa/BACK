package com.example.demo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommercialAffiliationDraftRequest extends AffiliationRegistrationRequest {

    private String commissionLocaleTpe;
    private String commissionEtrangereTpe;
    private String depotTpe;
    private String prixAchatTpe;
    private String prixLicenceTpe;
    private String abonnementPackage;
    private String commissionLocaleEcommerce;
    private String commissionEtrangereEcommerce;
    private String fraisMiseEnServiceEcommerce;
    private String commissionLocaleQrSoftpos;
    private String commissionEtrangereQrSoftpos;
    private String fraisServiceQrSoftpos;
    private String conditionsQrSoftpos;
    private String compteRenduQualification;
    private String compteRenduAcquereur;
    private String compteRenduOrigineProspect;
    private String compteRenduOrigineProspectDetail;
    private String compteRenduContactNomPrenom;
    private String compteRenduContactFonction;
    private String compteRenduPointVenteAcronyme;
    private String compteRenduActionnaires;
    private String compteRenduCommercant;
    private String compteRenduChaine;
    private String compteRenduRelationsLc;
    private String compteRenduDateOuverture;
    private String compteRenduNombreEmployes;
    private String compteRenduActivite;
    private String compteRenduMcc;
    private String compteRenduStandingMagasin;
    private String compteRenduNatureMarchandises;
    private String compteRenduSuperficieLocal;
    private String compteRenduStatutLocal;
    private String compteRenduChiffreAffairesAnnuel;
    private String compteRenduPartPaiementCarte;
    private String compteRenduPartCarteLocale;
    private String compteRenduProfilCommercant;
    private String compteRenduAppreciationVisite;
    private String compteRenduCommentaire;
    private String compteRenduFaitA;
    private String compteRenduDateVisite;
}
