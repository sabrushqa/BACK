package com.example.demo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AffiliationRegistrationRequest {

    private String typeCommercant;
    private String typeAffiliation;
    private String nom;
    private String raisonSociale;
    private String nomEntite;
    private String activite;
    private String secteur;
    private String mcc;
    private String telephonePrincipal;
    private String telephoneSecondaire;
    private String email;
    private String adresse;
    private String ville;
    private String region;
    private String chainePointVente;
    private String nombrePointsVente;
    private String pointVentesJson;
    private String prenom;
    private String cin;
    private String rc;
    private String ice;
    private String formeJuridique;
    private String representantLegal;
    private String numeroAutoEntrepreneur;
    private String objet;
    private String patente;
    private String fonction;
    private String beneficiairesEffectifs;
    private String dateNaissance;
    private String nationalite;
    private String modeMiseADispositionTpe;
    private String nombreTpe;
    private String equipementTpe;
    private String connectiviteTpe;
    private String commissionLocaleTpe;
    private String commissionEtrangereTpe;
    private String depotTpe;
    private String prixAchatTpe;
    private String prixLicenceTpe;
    private String modeServiceEcommerce;
    private String siteMarchandUrl;
    private String applicationMobile;
    private String commissionLocaleEcommerce;
    private String commissionEtrangereEcommerce;
    private String fraisMiseEnServiceEcommerce;
    private String modeleQrSoftpos;
    private String commissionLocaleQrSoftpos;
    private String commissionEtrangereQrSoftpos;
    private String fraisServiceQrSoftpos;
    private String conditionsQrSoftpos;
    private Boolean serviceCreditVoucher;
    private Boolean serviceAnnulation;
    private Boolean serviceDcc;
    private Boolean servicePreAutorisationCartePresente;
    private Boolean servicePreAutorisationCartePresenteConfirmationManuelle;
    private Boolean servicePreAutorisationManuelleConfirmationCartePresente;
    private Boolean serviceTransactionManuelle;
    private Boolean serviceTransactionManuelleSansCvv;
    private String rib;
    private String cinDocumentName;
    private String ribDocumentName;
    private String patenteDocumentName;
    private String statutsDocumentName;
    private String rcDocumentName;
    private String iceDocumentName;
    private String cinRepresentantDocumentName;
    private String pvNominationDocumentName;
    private String attestationAeDocumentName;
    private String cinSignataireDocumentName;
    private String pvAssociationDocumentName;
    private String listeMembresDocumentName;
}
