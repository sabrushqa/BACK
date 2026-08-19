package com.example.demo.entities;

import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeInteraction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "dossiers_affiliation")
@Getter
@Setter
@NoArgsConstructor
public class dossier_affiliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDossier;

    @Enumerated(EnumType.STRING)
    private StatusDossier status;

    @Enumerated(EnumType.STRING)
    private TypeAffiliation typeAffiliation;

    private String origineCreation;

    @Enumerated(EnumType.STRING)
    private ProspectStatus prospectStatus;

    private LocalDate derniereInteractionCommerciale;

    @Enumerated(EnumType.STRING)
    private TypeInteraction derniereInteractionType;

    private LocalDate prochaineRelanceCommerciale;

    @Enumerated(EnumType.STRING)
    private TypeInteraction prochaineRelanceType;

    private String rib;

    private LocalDate dateCreation;

    private LocalDate dateSoumission;

    private String modeMiseADispositionTpe;

    private Integer nombreTpe;

    private String equipementTpe;

    private String connectiviteTpe;

    private String commissionLocaleTpe;

    private String commissionEtrangereTpe;

    private String depotTpe;

    private String prixAchatTpe;

    private String prixLicenceTpe;

    private String abonnementPackage;

    private String modeServiceEcommerce;

    private String siteMarchandUrl;

    // Identifiant du site e-commerce affecte cote switch-monetique-service
    // (table site_ecommerce). Rempli uniquement au moment de l'affectation
    // reelle (voir SupervisorManagementService::assignEcommerceSiteToCommercant),
    // jamais a la creation du dossier — c'est l'equivalent, pour le canal
    // e-commerce, de l'affectation d'une reference TPE.
    private String idSiteEcommerceAffecte;

    private String applicationMobile;

    private String commissionLocaleEcommerce;

    private String commissionEtrangereEcommerce;

    private String fraisMiseEnServiceEcommerce;

    private String modeleQrSoftpos;

    // Quantite de dispositifs SoftPOS / codes QR demandes — pendant de
    // nombreTpe pour ce canal (voir AffiliationRegistrationService,
    // MerchantWorkspaceManagementService, StaffAffiliationManagementService).
    // Un seul champ partage entre SoftPOS et QR Code, comme modeleQrSoftpos
    // ci-dessus : ce canal ne suit pas de stock physique affecte (contrairement
    // a nombreTpe / isTpeAlreadyFullyAssigned), donc purement informatif.
    private Integer nombreQrSoftpos;

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

    @ManyToOne
    @JoinColumn(name = "commercant_id")
    private commercant commercant;

    @ManyToOne
    @JoinColumn(name = "commerciale_id")
    private commerciale commerciale;

    @ManyToOne
    @JoinColumn(name = "id_commerciale_assignee")
    private commerciale commercialeAssignee;

    private LocalDate dateAssignationCommerciale;

    @ManyToOne
    @JoinColumn(name = "back_office_id")
    private back_office backOffice;

    @ManyToOne
    @JoinColumn(name = "requested_pdv_id")
    private pdv requestedPdv;

    // true uniquement pour une extension (NOUVEAU_PDV) qui reutilise un point
    // de vente DEJA existant du commercant (juste plus de TPE dessus) plutot
    // que d'en creer un nouveau — requestedPdv pointe dans les deux cas vers
    // un pdv, ce flag est le seul moyen de distinguer les deux cas cote UI
    // (voir MerchantWorkspaceManagementService::requestNewPdvProduct).
    private Boolean requestedPdvDejaExistant;

    private String motifRefus;

    private LocalDate dateTraitementBackOffice;

    /**
     * Nombre de fois où le back office a renvoyé ce dossier au commercial pour correction.
     * Permet de distinguer, dans la file "en attente de validation BOA", une demande
     * jamais traitée d'une demande qui revient après correction.
     */
    private Integer nombreCorrections;

    /**
     * Dernier motif de correction transmis par le back office. Contrairement à
     * {@link #motifRefus} (effacé dès que le commercial retransmet le dossier), ce champ
     * reste renseigné après le renvoi au BOA afin de garder une trace visible de la
     * correction précédente.
     */
    private String dernierMotifCorrection;

    // ─── Relations vers les entités normalisées ───────────────────────────────

    /**
     * Contrats générés pour ce dossier (un par PDV ou par relance d'activation).
     * Utiliser cette collection à la place des champs generatedContractPath/
     * commercialReportPath/signedContractPath ci-dessous.
     */
    @OneToMany(mappedBy = "dossierAffiliation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("idContrat DESC")
    private List<contrat> contrats = new ArrayList<>();

    /**
     * Compte-rendus de visites commerciales pour ce dossier.
     * Utiliser cette collection à la place des champs compteRendu* ci-dessous.
     */
    @OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("idCompteRendu DESC")
    private List<compte_rendu> compteRendus = new ArrayList<>();

    // ─── Champs legacy — migration vers contrat ───────────────────────────────
    // TODO: Ces champs seront supprimés après migration SQL complète vers la table
    //       `contrats`. Utiliser dossier.getContrats().get(0) pour le dernier contrat.

    /** @deprecated Utiliser {@link contrat#getGeneratedContractPath()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String generatedContractPath;

    /** @deprecated Utiliser {@link contrat#getGeneratedContractFileName()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String generatedContractFileName;

    /** @deprecated Utiliser {@link contrat#getGeneratedContractAt()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private LocalDate generatedContractAt;

    /** @deprecated Utiliser {@link contrat#getCommercialReportPath()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String commercialReportPath;

    /** @deprecated Utiliser {@link contrat#getCommercialReportFileName()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String commercialReportFileName;

    /** @deprecated Utiliser {@link contrat#getCommercialReportGeneratedAt()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private LocalDate commercialReportGeneratedAt;

    /** @deprecated Utiliser {@link contrat#getSignedContractPath()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String signedContractPath;

    /** @deprecated Utiliser {@link contrat#getSignedContractFileName()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String signedContractFileName;

    /** @deprecated Utiliser {@link contrat#getSignedContractUploadedAt()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private LocalDate signedContractUploadedAt;

    // ─── Champs legacy — migration vers compte_rendu ──────────────────────────
    // TODO: Ces 27 champs seront supprimés après migration SQL vers la table
    //       `comptes_rendus`. Utiliser dossier.getCompteRendus().get(0) pour le dernier.

    /** @deprecated Utiliser {@link compte_rendu#getQualification()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduQualification;

    /** @deprecated Utiliser {@link compte_rendu#getAcquereur()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduAcquereur;

    /** @deprecated Utiliser {@link compte_rendu#getOrigineProspect()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduOrigineProspect;

    /** @deprecated Utiliser {@link compte_rendu#getOrigineProspectDetail()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduOrigineProspectDetail;

    /** @deprecated Utiliser {@link compte_rendu#getContactNomPrenom()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduContactNomPrenom;

    /** @deprecated Utiliser {@link compte_rendu#getContactFonction()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduContactFonction;

    /** @deprecated Utiliser {@link compte_rendu#getPointVenteAcronyme()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduPointVenteAcronyme;

    /** @deprecated Utiliser {@link compte_rendu#getActionnaires()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduActionnaires;

    /** @deprecated Utiliser {@link compte_rendu#getNomCommercant()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduCommercant;

    /** @deprecated Utiliser {@link compte_rendu#getChaine()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduChaine;

    /** @deprecated Utiliser {@link compte_rendu#getRelationsLc()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduRelationsLc;

    /** @deprecated Utiliser {@link compte_rendu#getDateOuverture()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduDateOuverture;

    /** @deprecated Utiliser {@link compte_rendu#getNombreEmployes()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduNombreEmployes;

    /** @deprecated Utiliser {@link compte_rendu#getActivite()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduActivite;

    /** @deprecated Utiliser {@link compte_rendu#getMcc()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduMcc;

    /** @deprecated Utiliser {@link compte_rendu#getStandingMagasin()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduStandingMagasin;

    /** @deprecated Utiliser {@link compte_rendu#getNatureMarchandises()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduNatureMarchandises;

    /** @deprecated Utiliser {@link compte_rendu#getSuperficieLocal()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduSuperficieLocal;

    /** @deprecated Utiliser {@link compte_rendu#getStatutLocal()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduStatutLocal;

    /** @deprecated Utiliser {@link compte_rendu#getChiffreAffairesAnnuel()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduChiffreAffairesAnnuel;

    /** @deprecated Utiliser {@link compte_rendu#getPartPaiementCarte()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduPartPaiementCarte;

    /** @deprecated Utiliser {@link compte_rendu#getPartCarteLocale()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduPartCarteLocale;

    /** @deprecated Utiliser {@link compte_rendu#getProfilCommercant()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduProfilCommercant;

    /** @deprecated Utiliser {@link compte_rendu#getAppreciationVisite()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduAppreciationVisite;

    /** @deprecated Utiliser {@link compte_rendu#getCommentaire()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduCommentaire;

    /** @deprecated Utiliser {@link compte_rendu#getFaitA()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduFaitA;

    /** @deprecated Utiliser {@link compte_rendu#getDateVisite()} */
    @Deprecated(since = "refactoring-god-table", forRemoval = true)
    private String compteRenduDateVisite;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) {
            dateCreation = LocalDate.now();
        }

        if (dateSoumission == null) {
            dateSoumission = LocalDate.now();
        }

        if (status == null) {
            status = StatusDossier.BROUILLON;
        }

        if (nombreCorrections == null) {
            nombreCorrections = 0;
        }
    }
}
