package com.example.demo.services;

import com.example.demo.entities.AE;
import com.example.demo.entities.Association;
import com.example.demo.entities.PM;
import com.example.demo.entities.PP;
import com.example.demo.entities.commercant;
import com.example.demo.entities.documents;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import java.util.List;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.repositories.AERepository;
import com.example.demo.repositories.AssociationRepository;
import com.example.demo.repositories.PMRepository;
import com.example.demo.repositories.PPRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GenerateurModeleContratAffiliation {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PPRepository ppRepository;
    private final PMRepository pmRepository;
    private final AERepository aeRepository;
    private final AssociationRepository associationRepository;
    private final String lanaCashLogoDataUri;
    private final String visaLogoDataUri;
    private final String mastercardLogoDataUri;
    private final String discoverLogoDataUri;
    private final String dinerLogoDataUri;
    private final String unionPayLogoDataUri;
    private final String marocPayLogoDataUri;

    public GenerateurModeleContratAffiliation(
        PPRepository ppRepository,
        PMRepository pmRepository,
        AERepository aeRepository,
        AssociationRepository associationRepository,
        PdfLogoProvider pdfLogoProvider
    ) {
        this.ppRepository = ppRepository;
        this.pmRepository = pmRepository;
        this.aeRepository = aeRepository;
        this.associationRepository = associationRepository;
        // Logos charges une seule fois pour toute l'application — voir
        // PdfLogoProvider (avant : chargement/encodage base64 duplique ici,
        // dans MerchantTicketService et dans ReclamationPdfService).
        this.lanaCashLogoDataUri = pdfLogoProvider.lanaCash();
        this.visaLogoDataUri = pdfLogoProvider.visa();
        this.mastercardLogoDataUri = pdfLogoProvider.mastercard();
        this.discoverLogoDataUri = pdfLogoProvider.discover();
        this.dinerLogoDataUri = pdfLogoProvider.diner();
        this.unionPayLogoDataUri = pdfLogoProvider.unionPay();
        this.marocPayLogoDataUri = pdfLogoProvider.marocPay();
    }

    public String genererHtmlFicheAutoAffiliation(
        dossier_affiliation dossier,
        List<documents> documentsDeposes
    ) {
        commercant merchant = dossier.getCommercant();
        MerchantProfile profile = resolveMerchantProfile(merchant);
        String merchantName = firstNotBlank(
            field(merchant == null ? null : merchant.getNomCommercial()),
            profile.raisonSociale(),
            profile.entityName(),
            "Commerçant"
        );

        StringBuilder identity = new StringBuilder();
        appendFicheRow(identity, "Nom / Raison sociale", merchantName);
        appendFicheRowIfPresent(identity, "CIN", profile.cin());
        appendFicheRowIfPresent(identity, "RC", profile.rc());
        appendFicheRowIfPresent(identity, "ICE", profile.ice());
        appendFicheRowIfPresent(identity, "Forme juridique", profile.formeJuridique());
        appendFicheRowIfPresent(identity, "Representant legal", profile.representantLegal());
        appendFicheRow(identity, "Type commerçant", formatLabel(merchant == null ? null : merchant.getType()));
        appendFicheRow(identity, "Activité", field(merchant == null ? null : merchant.getActivite()));
        appendFicheRow(identity, "Secteur", field(merchant == null ? null : merchant.getSecteur()));
        appendFicheRow(identity, "MCC", field(merchant == null ? null : merchant.getMcc()));
        appendFicheRowIfPresent(identity, "Chaine point de vente", merchant == null ? null : merchant.getChainePointVente());
        appendFicheRow(identity, "Nombre points de vente", safe(merchant == null ? null : merchant.getNombrePointsVente()));

        StringBuilder contact = new StringBuilder();
        appendFicheRow(contact, "E-mail", field(merchant == null ? null : merchant.getEmailContact()));
        appendFicheRow(contact, "Téléphone principal", field(merchant == null ? null : merchant.getTelephone()));
        appendFicheRowIfPresent(contact, "Téléphone secondaire", merchant == null ? null : merchant.getTelephoneSecondaire());
        appendFicheRow(contact, "Adresse", field(merchant == null ? null : merchant.getAdresse()));
        appendFicheRow(contact, "Ville", field(merchant == null ? null : merchant.getVille()));
        appendFicheRow(contact, "Region", field(merchant == null ? null : merchant.getRegion()));

        StringBuilder product = new StringBuilder();
        appendFicheRow(product, "Type affiliation", formatLabel(dossier.getTypeAffiliation()));
        appendFicheRow(product, "Statut", formatLabel(dossier.getStatus()));
        appendFicheRow(product, "Statut prospect", formatLabel(dossier.getProspectStatus()));
        appendFicheRow(product, "Date soumission", formatOptionalDate(safe(dossier.getDateSoumission())));
        appendFicheRow(product, "RIB", safe(dossier.getRib()));
        appendFicheRowIfPresent(product, "Nombre TPE", safe(dossier.getNombreTpe()));
        appendFicheRowIfPresent(product, "Mode mise à disposition", dossier.getModeMiseADispositionTpe());
        appendFicheRowIfPresent(product, "Site marchand", dossier.getSiteMarchandUrl());
        appendFicheRowIfPresent(product, "Modèle QR / SoftPOS", dossier.getModeleQrSoftpos());

        StringBuilder reportAndContract = new StringBuilder();
        appendFicheRowIfPresent(reportAndContract, "Qualification", dossier.getCompteRenduQualification());
        appendFicheRowIfPresent(reportAndContract, "Appréciation visite", dossier.getCompteRenduAppreciationVisite());
        appendFicheRowIfPresent(reportAndContract, "Contrat généré", dossier.getGeneratedContractFileName());
        if (dossier.getGeneratedContractAt() != null) {
            appendFicheRow(reportAndContract, "Date génération contrat", DATE_FORMATTER.format(dossier.getGeneratedContractAt()));
        }
        appendFicheRowIfPresent(reportAndContract, "Contrat signé", dossier.getSignedContractFileName());
        if (dossier.getSignedContractUploadedAt() != null) {
            appendFicheRow(reportAndContract, "Date dépôt contrat signé", DATE_FORMATTER.format(dossier.getSignedContractUploadedAt()));
        }

        StringBuilder documentsHtml = new StringBuilder();
        if (documentsDeposes == null || documentsDeposes.isEmpty()) {
            documentsHtml.append("<span>Aucun document déposé sur ce dossier.</span>");
        } else {
            for (documents document : documentsDeposes) {
                appendFicheRow(
                    documentsHtml,
                    formatLabel(document.getTypeDocument()),
                    extractFileName(document.getCheminStockage())
                );
            }
        }

        String printedAt = DATE_FORMATTER.format(LocalDate.now());
        boolean isProspection = "COMMERCIAL_DIRECT".equalsIgnoreCase(safe(dossier.getOrigineCreation()));
        String coverTitle = isProspection ? "Dossier de prospection commerciale" : "Dossier d'auto-affiliation";
        String ficheTitle = isProspection ? "Fiche de prospection commerciale" : "Fiche d'auto-affiliation";

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8" />
            <style>
              @page { size: A4; margin: 14mm; }
              body { font-family: Arial, Helvetica, sans-serif; color: #0d2b45; margin: 0; }
              .cover-page {
                height: 100vh;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                text-align: center;
                page-break-after: always;
              }
              .cover-logo { height: 90px; margin-bottom: 24px; }
              .cover-title { font-size: 24px; font-weight: bold; margin: 0 0 8px; }
              .cover-sub { font-size: 13px; color: #6a91aa; margin: 4px 0; }
              .fiche-head { display: flex; align-items: center; gap: 12px; border-bottom: 2px solid #0d2b45; padding-bottom: 8px; margin-bottom: 10px; }
              .fiche-logo { height: 32px; }
              .fiche-title h1 { font-size: 15px; margin: 0; }
              .fiche-title span { font-size: 9px; color: #6a91aa; }
              .fiche-grid { display: flex; flex-wrap: wrap; gap: 0 16px; font-size: 9.5px; }
              .fiche-section { width: 48%; margin-bottom: 8px; }
              .fiche-section.full { width: 100%; }
              .fiche-section h2 { font-size: 10.5px; margin: 0 0 4px; padding-bottom: 2px; border-bottom: 1px solid #cddfe9; color: #1a6fa8; }
              .row-list span { display: flex; gap: 4px; line-height: 1.3; }
              .row-list strong { min-width: 42%; }
              .fiche-foot { margin-top: 10px; padding-top: 6px; border-top: 1px solid #cddfe9; font-size: 7.5px; color: #6a91aa; }
            </style>
            </head>
            <body>

            <div class="cover-page">
              <img class="cover-logo" src="%LOGO%" />
              <p class="cover-title">%COVER_TITLE%</p>
              <p class="cover-sub">%MERCHANT%</p>
              <p class="cover-sub">Dossier n°%DOSSIER_ID% &#183; %PRODUCT%</p>
              <p class="cover-sub">Genere le %PRINTED_AT%</p>
            </div>

            <header class="fiche-head">
              <img class="fiche-logo" src="%LOGO%" />
              <div class="fiche-title">
                <h1>%FICHE_TITLE%</h1>
                <span>Dossier #%DOSSIER_ID% &#183; Genere le %PRINTED_AT%</span>
              </div>
            </header>

            <div class="fiche-grid">
              <section class="fiche-section">
                <h2>Identite &amp; activite</h2>
                <div class="row-list">%IDENTITY%</div>
              </section>
              <section class="fiche-section">
                <h2>Coordonnees</h2>
                <div class="row-list">%CONTACT%</div>
              </section>
              <section class="fiche-section">
                <h2>Produit &amp; configuration</h2>
                <div class="row-list">%PRODUCT_ROWS%</div>
              </section>
              <section class="fiche-section">
                <h2>Compte-rendu &amp; contrat</h2>
                <div class="row-list">%REPORT_CONTRACT%</div>
              </section>
              <section class="fiche-section full">
                <h2>Documents deposes</h2>
                <div class="row-list">%DOCUMENTS%</div>
              </section>
            </div>

            <footer class="fiche-foot">
              Lana Cash - Portail d'affiliation. Document genere automatiquement, a conserver dans le dossier physique du commercant.
            </footer>

            </body>
            </html>
            """
            .replace("%LOGO%", lanaCashLogoDataUri)
            .replace("%COVER_TITLE%", escapeHtml(coverTitle))
            .replace("%FICHE_TITLE%", escapeHtml(ficheTitle))
            .replace("%MERCHANT%", escapeHtml(merchantName))
            .replace("%DOSSIER_ID%", safe(dossier.getIdDossier()))
            .replace("%PRODUCT%", escapeHtml(formatLabel(dossier.getTypeAffiliation())))
            .replace("%PRINTED_AT%", printedAt)
            .replace("%IDENTITY%", identity.toString())
            .replace("%CONTACT%", contact.toString())
            .replace("%PRODUCT_ROWS%", product.toString())
            .replace("%REPORT_CONTRACT%", reportAndContract.toString())
            .replace("%DOCUMENTS%", documentsHtml.toString());
    }

    private void appendFicheRowIfPresent(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            appendFicheRow(builder, label, value);
        }
    }

    private void appendFicheRow(StringBuilder builder, String label, String value) {
        String safeValue = StringUtils.hasText(value) ? value : "-";
        builder.append("<span><strong>")
            .append(escapeHtml(label))
            .append("</strong>")
            .append(escapeHtml(safeValue))
            .append("</span>");
    }

    private String extractFileName(String storedPath) {
        if (storedPath == null) {
            return "";
        }
        String normalized = storedPath.trim().replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String formatLabel(Object value) {
        String raw = safe(value);
        if (raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        boolean capitalizeNext = true;
        for (char character : lower.toCharArray()) {
            if (capitalizeNext && Character.isLetter(character)) {
                builder.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                builder.append(character);
            }
            capitalizeNext = character == ' ';
        }
        return builder.toString();
    }

    public String genererHtmlContrat(dossier_affiliation dossier) {
        return genererHtmlContrat(dossier, null);
    }

    public String genererHtmlContrat(dossier_affiliation dossier, pdv pointVente) {
        DonneesModeleContrat data = construireDonneesModele(dossier, pointVente);
        return dossier.getTypeAffiliation() == TypeAffiliation.E_COMMERCE
            ? genererHtmlContratEcommerce(data)
            : genererHtmlContratEncaissement(data);
    }

    /**
     * Force le rendu du contrat e-commerce independamment du type du dossier -
     * utilise pour les dossiers combines (ENCAISSEMENT_ET_ECOMMERCE) dont le
     * contrat encaissement est deja rendu via genererHtmlContrat(dossier, pdv).
     */
    public String genererHtmlContratEcommerceForce(dossier_affiliation dossier) {
        return genererHtmlContratEcommerce(construireDonneesModele(dossier, null));
    }

    public String genererHtmlCompteRenduCommercial(dossier_affiliation dossier) {
        return genererHtmlCompteRenduCommercial(dossier, CommercialReportLayoutMode.STANDARD);
    }

    public String genererHtmlCompteRenduCommercial(
        dossier_affiliation dossier,
        CommercialReportLayoutMode layoutMode
    ) {
        DonneesCompteRenduCommercial data = construireDonneesCompteRenduCommercial(dossier);
        String html = loadResourceAsString("contrats/compte_rendu_commercial.html");
        CommercialReportLayoutMode resolvedLayout = layoutMode == null
            ? CommercialReportLayoutMode.STANDARD
            : layoutMode;

        html = replaceTemplateToken(html, "layoutModeClass", escapeHtml(resolvedLayout.cssClass()));
        html = replaceTemplateToken(html, "pageMargin", resolvedLayout.pageMargin());
        html = replaceTemplateToken(html, "logoUrl", escapeHtml(data.logoUrl()));
        html = replaceTemplateToken(
            html,
            "qualificationAffilieCheck",
            checkbox(matchesOption(data.qualification(), "AFFILIE"))
        );
        html = replaceTemplateToken(
            html,
            "qualificationProspectCheck",
            checkbox(matchesOption(data.qualification(), "PROSPECT"))
        );
        html = replaceTemplateToken(
            html,
            "acquereur",
            commercialReportWideValue(data.acquereur(), resolvedLayout)
        );
        html = replaceTemplateToken(
            html,
            "origineProspectionDirecteCheck",
            checkbox(
                matchesOption(
                    data.origineProspect(),
                    "PROSPECTION_DIRECTE",
                    "DIRECTE"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "origineMasterFranchiseCheck",
            checkbox(matchesOption(data.origineProspect(), "MASTER_FRANCHISE"))
        );
        html = replaceTemplateToken(
            html,
            "origineCihCheck",
            checkbox(matchesOption(data.origineProspect(), "CIH"))
        );
        html = replaceTemplateToken(
            html,
            "origineUmniaCheck",
            checkbox(matchesOption(data.origineProspect(), "UMNIA"))
        );
        html = replaceTemplateToken(
            html,
            "origineApporteurCheck",
            checkbox(
                matchesOption(
                    data.origineProspect(),
                    "APPORTEUR_AFFAIRES",
                    "APPORTEUR"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "origineApporteurDetail",
            templateValue(
                matchesOption(
                    data.origineProspect(),
                    "APPORTEUR_AFFAIRES",
                    "APPORTEUR"
                )
                    ? data.origineProspectDetail()
                    : ""
            )
        );
        html = replaceTemplateToken(
            html,
            "origineAutreBanqueCheck",
            checkbox(
                matchesOption(
                    data.origineProspect(),
                    "AUTRE_BANQUES",
                    "AUTRE_BANQUE",
                    "AUTRE"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "origineAutreBanqueDetail",
            templateValue(
                matchesOption(
                    data.origineProspect(),
                    "AUTRE_BANQUES",
                    "AUTRE_BANQUE",
                    "AUTRE"
                )
                    ? data.origineProspectDetail()
                    : ""
            )
        );
        html = replaceTemplateToken(
            html,
            "contactNomPrenom",
            templateValue(data.contactNomPrenom())
        );
        html = replaceTemplateToken(
            html,
            "contactFonction",
            templateValue(data.contactFonction())
        );
        html = replaceTemplateToken(
            html,
            "pointVenteAcronyme",
            templateValue(data.pointVenteAcronyme())
        );
        html = replaceTemplateToken(html, "actionnaires", templateValue(data.actionnaires()));
        html = replaceTemplateToken(html, "commerçant", templateValue(data.commercant()));
        html = replaceTemplateToken(html, "chaine", templateValue(data.chaine()));
        html = replaceTemplateToken(
            html,
            "relationsLc",
            commercialReportWideValue(data.relationsLc(), resolvedLayout)
        );
        html = replaceTemplateToken(
            html,
            "dateOuverture",
            templateValue(data.dateOuverture())
        );
        html = replaceTemplateToken(
            html,
            "nombreEmployes",
            templateValue(data.nombreEmployes())
        );
        html = replaceTemplateToken(html, "activité", templateValue(data.activite()));
        html = replaceTemplateToken(html, "mcc", templateValue(data.mcc()));
        html = replaceTemplateToken(
            html,
            "standingMagasin",
            templateValue(data.standingMagasin())
        );
        html = replaceTemplateToken(
            html,
            "natureMarchandises",
            commercialReportWideValue(data.natureMarchandises(), resolvedLayout)
        );
        html = replaceTemplateToken(
            html,
            "superficie1030Check",
            checkbox(
                matchesOption(
                    data.superficieLocal(),
                    "DE_10_A_30_M2",
                    "10_30",
                    "SURFACE_10_30"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "superficie30100Check",
            checkbox(
                matchesOption(
                    data.superficieLocal(),
                    "ENTRE_30_ET_100_M2",
                    "30_100",
                    "SURFACE_30_100"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "superficie100PlusCheck",
            checkbox(
                matchesOption(
                    data.superficieLocal(),
                    "AU_DELA_100_M2",
                    "PLUS_100",
                    "SURFACE_100_PLUS"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "localAcheteCheck",
            checkbox(matchesOption(data.statutLocal(), "ACHETE"))
        );
        html = replaceTemplateToken(
            html,
            "localLoueCheck",
            checkbox(matchesOption(data.statutLocal(), "LOUE"))
        );
        html = replaceTemplateToken(
            html,
            "caMoins05Check",
            checkbox(
                matchesOption(
                    data.chiffreAffairesAnnuel(),
                    "MOINS_0_5_MDHS",
                    "CA_MOINS_0_5"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "ca051Check",
            checkbox(
                matchesOption(
                    data.chiffreAffairesAnnuel(),
                    "DE_0_5_A_1_MDHS",
                    "CA_0_5_1"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "ca15Check",
            checkbox(
                matchesOption(
                    data.chiffreAffairesAnnuel(),
                    "DE_1_A_5_MDHS",
                    "CA_1_5"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "caPlus5Check",
            checkbox(
                matchesOption(
                    data.chiffreAffairesAnnuel(),
                    "PLUS_5_MDHS",
                    "CA_PLUS_5"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "partPaiementCarte",
            templateValue(formatPercent(data.partPaiementCarte()))
        );
        html = replaceTemplateToken(
            html,
            "partCarteLocale",
            templateValue(formatPercent(data.partCarteLocale()))
        );
        html = replaceTemplateToken(
            html,
            "profilOffshoreCheck",
            checkbox(
                matchesOption(
                    data.profilCommercant(),
                    "OFF_SHORE_VIREMENT",
                    "OFFSHORE"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "profilInshoreCheck",
            checkbox(
                matchesOption(
                    data.profilCommercant(),
                    "IN_SHORE_VIREMENT",
                    "INSHORE"
                )
            )
        );
        html = replaceTemplateToken(
            html,
            "appreciationFavorableCheck",
            checkbox(matchesOption(data.appreciationVisite(), "FAVORABLE"))
        );
        html = replaceTemplateToken(
            html,
            "appreciationDefavorableCheck",
            checkbox(matchesOption(data.appreciationVisite(), "DEFAVORABLE"))
        );
        html = replaceTemplateToken(
            html,
            "commentaire",
            commercialReportCommentValue(data.commentaire(), resolvedLayout)
        );
        html = replaceTemplateToken(html, "faitA", templateValue(data.faitA()));
        html = replaceTemplateToken(html, "dateVisite", templateValue(data.dateVisite()));
        html = replaceTemplateToken(
            html,
            "commercialNom",
            templateValue(data.commercialNom())
        );
        return html;
    }

    private DonneesModeleContrat construireDonneesModele(
        dossier_affiliation dossier,
        pdv pointVente
    ) {
        commercant merchant = dossier.getCommercant();
        MerchantProfile profile = resolveMerchantProfile(merchant);
        String entityName = firstNotBlank(
            profile.entityName(),
            field(merchant == null ? null : merchant.getRaisonSociale()),
            field(merchant == null ? null : merchant.getNomCommercial()),
            "Commerçant"
        );
        String signatoryName = firstNotBlank(profile.signatoryName(), profile.representantLegal(), entityName);

        return new DonneesModeleContrat(
            "AFF-" + safe(dossier.getIdDossier()),
            DATE_FORMATTER.format(LocalDate.now()),
            lanaCashLogoDataUri,
            visaLogoDataUri,
            mastercardLogoDataUri,
            discoverLogoDataUri,
            dinerLogoDataUri,
            unionPayLogoDataUri,
            marocPayLogoDataUri,
            safe(merchant == null ? null : merchant.getIdCommercant()),
            firstNotBlank(
                pointVente == null || pointVente.getIdPDV() == null
                    ? ""
                    : "PDV-" + pointVente.getIdPDV(),
                field(merchant == null ? null : merchant.getChainePointVente()),
                merchant == null || merchant.getNombrePointsVente() == null
                    ? ""
                    : String.valueOf(merchant.getNombrePointsVente())
            ),
            entityName,
            firstNotBlank(
                field(pointVente == null ? null : pointVente.getNomPDV()),
                field(merchant == null ? null : merchant.getNomCommercial()),
                entityName
            ),
            signatoryName,
            mapMerchantTypeLabel(merchant == null ? null : merchant.getType()),
            mapAffiliationTypeLabel(dossier.getTypeAffiliation()),
            resolveAffiliationPackageLabel(dossier),
            field(merchant == null ? null : merchant.getActivite()),
            field(merchant == null ? null : merchant.getSecteur()),
            field(merchant == null ? null : merchant.getMcc()),
            firstNotBlank(
                field(pointVente == null ? null : pointVente.getAdresse()),
                field(merchant == null ? null : merchant.getAdresse())
            ),
            field(pointVente == null ? null : pointVente.getQuartier()),
            firstNotBlank(
                field(pointVente == null ? null : pointVente.getVille()),
                field(merchant == null ? null : merchant.getVille())
            ),
            field(pointVente == null ? null : pointVente.getCodePostal()),
            field(merchant == null ? null : merchant.getRegion()),
            firstNotBlank(
                field(pointVente == null ? null : pointVente.getEmail()),
                field(merchant == null ? null : merchant.getEmailContact())
            ),
            firstNotBlank(
                field(pointVente == null ? null : pointVente.getTelephone()),
                field(merchant == null ? null : merchant.getTelephone())
            ),
            field(merchant == null ? null : merchant.getTelephoneSecondaire()),
            firstNotBlank(profile.raisonSociale(), field(merchant == null ? null : merchant.getRaisonSociale()), entityName),
            profile.formeJuridique(),
            firstNotBlank(profile.rc(), field(merchant == null ? null : merchant.getRegistreCommerce())),
            firstNotBlank(profile.ice(), field(merchant == null ? null : merchant.getIdentifiantFiscal())),
            profile.cin(),
            profile.representantLegal(),
            field(merchant == null ? null : merchant.getPatente()),
            field(merchant == null ? null : merchant.getFonction()),
            field(merchant == null ? null : merchant.getBeneficiairesEffectifs()),
            formatOptionalDate(field(merchant == null ? null : merchant.getDateNaissance())),
            field(merchant == null ? null : merchant.getNationalite()),
            safe(dossier.getRib()),
            safe(dossier.getModeServiceEcommerce()),
            safe(dossier.getSiteMarchandUrl()),
            safe(dossier.getApplicationMobile()),
            safe(dossier.getCommissionLocaleEcommerce()),
            safe(dossier.getCommissionEtrangereEcommerce()),
            safe(dossier.getFraisMiseEnServiceEcommerce()),
            safe(dossier.getModeMiseADispositionTpe()),
            dossier.getNombreTpe() == null ? "" : String.valueOf(dossier.getNombreTpe()),
            safe(dossier.getEquipementTpe()),
            safe(dossier.getConnectiviteTpe()),
            safe(dossier.getCommissionLocaleTpe()),
            safe(dossier.getCommissionEtrangereTpe()),
            safe(dossier.getDepotTpe()),
            safe(dossier.getPrixAchatTpe()),
            safe(dossier.getPrixLicenceTpe()),
            safe(dossier.getModeleQrSoftpos()),
            safe(dossier.getCommissionLocaleQrSoftpos()),
            safe(dossier.getCommissionEtrangereQrSoftpos()),
            safe(dossier.getFraisServiceQrSoftpos()),
            safe(dossier.getConditionsQrSoftpos()),
            Boolean.TRUE.equals(dossier.getServiceCreditVoucher()),
            Boolean.TRUE.equals(dossier.getServiceAnnulation()),
            Boolean.TRUE.equals(dossier.getServiceDcc()),
            Boolean.TRUE.equals(dossier.getServicePreAutorisationCartePresente()),
            Boolean.TRUE.equals(dossier.getServicePreAutorisationCartePresenteConfirmationManuelle()),
            Boolean.TRUE.equals(dossier.getServicePreAutorisationManuelleConfirmationCartePresente()),
            Boolean.TRUE.equals(dossier.getServiceTransactionManuelle()),
            Boolean.TRUE.equals(dossier.getServiceTransactionManuelleSansCvv())
        );
    }

    private DonneesCompteRenduCommercial construireDonneesCompteRenduCommercial(
        dossier_affiliation dossier
    ) {
        commercant merchant = dossier.getCommercant();
        MerchantProfile profile = resolveMerchantProfile(merchant);
        String merchantName = firstNotBlank(
            safe(dossier.getCompteRenduCommercant()),
            field(merchant == null ? null : merchant.getNomCommercial()),
            field(merchant == null ? null : merchant.getRaisonSociale()),
            "Commerçant"
        );

        return new DonneesCompteRenduCommercial(
            lanaCashLogoDataUri,
            safe(dossier.getCompteRenduQualification()),
            safe(dossier.getCompteRenduAcquereur()),
            safe(dossier.getCompteRenduOrigineProspect()),
            safe(dossier.getCompteRenduOrigineProspectDetail()),
            firstNotBlank(
                safe(dossier.getCompteRenduContactNomPrenom()),
                profile.representantLegal(),
                merchantName
            ),
            safe(dossier.getCompteRenduContactFonction()),
            firstNotBlank(
                safe(dossier.getCompteRenduPointVenteAcronyme()),
                field(merchant == null ? null : merchant.getChainePointVente())
            ),
            safe(dossier.getCompteRenduActionnaires()),
            merchantName,
            firstNotBlank(
                safe(dossier.getCompteRenduChaine()),
                field(merchant == null ? null : merchant.getChainePointVente())
            ),
            safe(dossier.getCompteRenduRelationsLc()),
            formatOptionalDate(safe(dossier.getCompteRenduDateOuverture())),
            safe(dossier.getCompteRenduNombreEmployes()),
            firstNotBlank(
                safe(dossier.getCompteRenduActivite()),
                field(merchant == null ? null : merchant.getActivite())
            ),
            firstNotBlank(
                safe(dossier.getCompteRenduMcc()),
                field(merchant == null ? null : merchant.getMcc())
            ),
            safe(dossier.getCompteRenduStandingMagasin()),
            safe(dossier.getCompteRenduNatureMarchandises()),
            safe(dossier.getCompteRenduSuperficieLocal()),
            safe(dossier.getCompteRenduStatutLocal()),
            safe(dossier.getCompteRenduChiffreAffairesAnnuel()),
            safe(dossier.getCompteRenduPartPaiementCarte()),
            safe(dossier.getCompteRenduPartCarteLocale()),
            safe(dossier.getCompteRenduProfilCommercant()),
            safe(dossier.getCompteRenduAppreciationVisite()),
            safe(dossier.getCompteRenduCommentaire()),
            firstNotBlank(
                safe(dossier.getCompteRenduFaitA()),
                field(merchant == null ? null : merchant.getVille())
            ),
            formatOptionalDate(safe(dossier.getCompteRenduDateVisite())),
            resolveCommercialName(dossier)
        );
    }

    private MerchantProfile resolveMerchantProfile(commercant merchant) {
        if (merchant == null || merchant.getIdCommercant() == null || merchant.getType() == null) {
            return MerchantProfile.empty();
        }

        Long merchantId = merchant.getIdCommercant();
        return switch (merchant.getType()) {
            case PERSONNE_PHYSIQUE -> ppRepository.findByCommercant_IdCommercant(merchantId)
                .map(this::mapPersonnePhysique)
                .orElse(MerchantProfile.empty());
            case PERSONNE_MORALE -> pmRepository.findByCommercant_IdCommercant(merchantId)
                .map(this::mapPersonneMorale)
                .orElse(MerchantProfile.empty());
            case AUTO_ENTREPRENEUR -> aeRepository.findByCommercant_IdCommercant(merchantId)
                .map(this::mapAutoEntrepreneur)
                .orElse(MerchantProfile.empty());
            case ASSOCIATION_FONDATION -> associationRepository.findByCommercant_IdCommercant(merchantId)
                .map(this::mapAssociation)
                .orElse(MerchantProfile.empty());
        };
    }

    private MerchantProfile mapPersonnePhysique(PP pp) {
        String fullName = fullName(pp.getPrenom(), pp.getNom());
        return new MerchantProfile(fullName, fullName, fullName, "Personne physique", "", "", safe(pp.getCin()), fullName);
    }

    private MerchantProfile mapPersonneMorale(PM pm) {
        return new MerchantProfile(
            safe(pm.getRaisonSociale()),
            safe(pm.getRaisonSociale()),
            safe(pm.getRepresentantLegal()),
            safe(pm.getFormeJuridique()),
            safe(pm.getRegistreCommerce()),
            safe(pm.getIce()),
            "",
            safe(pm.getRepresentantLegal())
        );
    }

    private MerchantProfile mapAutoEntrepreneur(AE ae) {
        String fullName = fullName(ae.getPrenom(), ae.getNom());
        return new MerchantProfile(fullName, fullName, fullName, "Auto-entrepreneur", "", "", "", fullName);
    }

    private MerchantProfile mapAssociation(Association association) {
        return new MerchantProfile(
            safe(association.getNomEntite()),
            safe(association.getNomEntite()),
            safe(association.getRepresentantLegal()),
            "Association / Fondation",
            "",
            "",
            "",
            safe(association.getRepresentantLegal())
        );
    }

    private String genererHtmlContratEncaissement(DonneesModeleContrat data) {
        boolean isTpe = "TPE".equals(data.affiliationTypeLabel());
        boolean isSoftpos = "SoftPOS".equals(data.affiliationTypeLabel());
        boolean isQrCode = "QR Code".equals(data.affiliationTypeLabel());
        String packageLabel = Objects.requireNonNullElse(data.affiliationPackageLabel(), "");
        String packageKey = packageLabel
            .toUpperCase(Locale.ROOT)
            .replace('É', 'E')
            .replace('È', 'E')
            .replace('Ê', 'E')
            .replace('Ë', 'E');
        boolean packageDecouverte = packageKey.contains("DECOUVERTE");
        boolean packageEssentiel = packageKey.contains("ESSENTIEL");
        boolean packagePremium = packageKey.contains("PREMIUM");
        String legalRepresentative = firstNotBlank(data.representantLegal(), data.signatoryName());
        String activityOrSector = firstNotBlank(data.secteur(), data.activite());
        BankAccountData bankAccount = parseBankAccount(data.rib());
        String html = loadResourceAsString("contrats/contrat_affiliation_encaissement.html");

        String commissionLocale = firstNotBlank(
            isTpe ? data.commissionLocaleTpe() : data.commissionLocaleQrSoftpos(),
            data.commissionLocaleTpe(),
            data.commissionLocaleQrSoftpos()
        );
        String commissionEtrangere = firstNotBlank(
            isTpe ? data.commissionEtrangereTpe() : data.commissionEtrangereQrSoftpos(),
            data.commissionEtrangereTpe(),
            data.commissionEtrangereQrSoftpos()
        );

        html = replaceTemplateToken(html, "creationCheck", templateCheckbox(true));
        html = replaceTemplateToken(html, "avenantCheck", templateCheckbox(false));
        html = replaceTemplateToken(html, "merchantNumber", templateValue(firstNotBlank(data.merchantNumber(), data.reference())));
        html = replaceTemplateToken(html, "pointOfSaleReference", templateValue(data.pointOfSaleReference()));
        html = replaceTemplateToken(html, "mcc", templateValue(data.mcc()));
        html = replaceTemplateToken(html, "raisonSociale", templateValue(firstNotBlank(data.raisonSociale(), data.entityName())));
        html = replaceTemplateToken(html, "formeJuridique", templateValue(data.formeJuridique()));
        html = replaceTemplateToken(html, "secteurActivite", templateValue(activityOrSector));
        html = replaceTemplateToken(html, "enseigne", templateValue(data.enseigne()));
        html = replaceTemplateToken(html, "adresse", templateValue(data.adresse()));
        html = replaceTemplateToken(html, "quartier", templateValue(data.quartier()));
        html = replaceTemplateToken(html, "codePostal", templateValue(data.codePostal()));
        html = replaceTemplateToken(html, "ville", templateValue(data.ville()));
        html = replaceTemplateToken(html, "ice", templateValue(data.ice()));
        html = replaceTemplateToken(html, "rc", templateValue(data.rc()));
        html = replaceTemplateToken(html, "patente", templateValue(data.patente()));
        html = replaceTemplateToken(html, "representantLegal", templateValue(legalRepresentative));
        html = replaceTemplateToken(html, "fonction", templateValue(data.fonction()));
        html = replaceTemplateToken(html, "telephoneFixe", templateValue(data.telephoneSecondaire()));
        html = replaceTemplateToken(html, "gsm", templateValue(firstNotBlank(data.telephonePrincipal(), data.telephoneSecondaire())));
        html = replaceTemplateToken(html, "emailReleve", templateValue(data.email()));
        html = replaceTemplateToken(html, "beneficiairesEffectifs", templateValue(data.beneficiairesEffectifs()));
        html = replaceTemplateToken(html, "cin", templateValue(data.cin()));
        html = replaceTemplateToken(html, "dateNaissance", templateValue(data.dateNaissance()));
        html = replaceTemplateToken(html, "nationalite", templateValue(data.nationalite()));
        html = replaceTemplateToken(html, "typeTpeCheck", templateCheckbox(isTpe));
        html = replaceTemplateToken(html, "typeSoftposCheck", templateCheckbox(isSoftpos));
        html = replaceTemplateToken(html, "typeQrCheck", templateCheckbox(isQrCode));
        html = replaceTemplateToken(html, "modeMiseADispositionTpe", templateValue(data.modeMiseADispositionTpe()));
        html = replaceTemplateToken(html, "equipementTpe", templateValue(data.equipementTpe()));
        html = replaceTemplateToken(html, "connectiviteTpe", templateValue(data.connectiviteTpe()));
        html = replaceTemplateToken(html, "nombreTpe", templateValue(data.nombreTpe()));
        html = replaceTemplateToken(html, "modeleQrSoftpos", templateValue(data.modeleQrSoftpos()));
        html = replaceTemplateToken(html, "commissionLocale", templateValue(commissionLocale));
        html = replaceTemplateToken(html, "commissionEtrangere", templateValue(commissionEtrangere));
        html = replaceTemplateToken(html, "depotTpe", templateValue(data.depotTpe()));
        html = replaceTemplateToken(html, "prixAchatTpe", templateValue(data.prixAchatTpe()));
        html = replaceTemplateToken(html, "prixLicenceTpe", templateValue(data.prixLicenceTpe()));
        html = replaceTemplateToken(html, "fraisServiceQrSoftpos", templateValue(data.fraisServiceQrSoftpos()));
        html = replaceTemplateToken(html, "serviceCreditVoucherCheck", templateCheckbox(data.serviceCreditVoucher()));
        html = replaceTemplateToken(html, "serviceAnnulationCheck", templateCheckbox(data.serviceAnnulation()));
        html = replaceTemplateToken(html, "serviceDccCheck", templateCheckbox(data.serviceDcc()));
        html = replaceTemplateToken(
            html,
            "servicePreAutorisationCartePresenteCheck",
            templateCheckbox(data.servicePreAutorisationCartePresente())
        );
        html = replaceTemplateToken(
            html,
            "servicePreAutorisationCartePresenteConfirmationManuelleCheck",
            templateCheckbox(data.servicePreAutorisationCartePresenteConfirmationManuelle())
        );
        html = replaceTemplateToken(
            html,
            "servicePreAutorisationManuelleConfirmationCartePresenteCheck",
            templateCheckbox(data.servicePreAutorisationManuelleConfirmationCartePresente())
        );
        html = replaceTemplateToken(
            html,
            "serviceTransactionManuelleCheck",
            templateCheckbox(data.serviceTransactionManuelle())
        );
        html = replaceTemplateToken(
            html,
            "serviceTransactionManuelleSansCvvCheck",
            templateCheckbox(data.serviceTransactionManuelleSansCvv())
        );
        html = replaceTemplateToken(html, "affiliationPackageLabel", templateValue(data.affiliationPackageLabel()));
        html = replaceTemplateToken(html, "packageDecouverteCheck", templateCheckbox(packageDecouverte));
        html = replaceTemplateToken(html, "packageEssentielCheck", templateCheckbox(packageEssentiel));
        html = replaceTemplateToken(html, "packagePremiumCheck", templateCheckbox(packagePremium));
        html = replaceTemplateToken(html, "bankCodeCells", ribTemplateCells(bankAccount.bankCode(), 3));
        html = replaceTemplateToken(html, "cityCodeCells", ribTemplateCells(bankAccount.cityCode(), 3));
        html = replaceTemplateToken(html, "accountNumberCells", ribTemplateCells(bankAccount.accountNumber(), 16));
        html = replaceTemplateToken(html, "ribKeyCells", ribTemplateCells(bankAccount.ribKey(), 2));
        html = replaceTemplateToken(html, "currencyCells", ribTemplateCells(bankAccount.currency(), 3));
        html = replaceTemplateToken(html, "faitAVille", templateValue(data.ville()));
        html = replaceTemplateToken(html, "contractDate", templateValue(data.dateLabel()));
        html = replaceTemplateToken(html, "logoUrl", escapeHtml(data.logoUrl()));
        html = replaceTemplateToken(html, "visaLogoUrl", escapeHtml(data.visaLogoUrl()));
        html = replaceTemplateToken(html, "mastercardLogoUrl", escapeHtml(data.mastercardLogoUrl()));
        html = replaceTemplateToken(html, "discoverLogoUrl", escapeHtml(data.discoverLogoUrl()));
        html = replaceTemplateToken(html, "dinerLogoUrl", escapeHtml(data.dinerLogoUrl()));
        html = replaceTemplateToken(html, "unionPayLogoUrl", escapeHtml(data.unionPayLogoUrl()));
        html = replaceTemplateToken(html, "marocPayLogoUrl", escapeHtml(data.marocPayLogoUrl()));
        return html;
    }

    private String genererHtmlContratEcommerce(DonneesModeleContrat data) {
        String mode = safe(data.modeServiceEcommerce());
        String legalRepresentative = firstNotBlank(data.representantLegal(), data.signatoryName());
        String activityOrSector = firstNotBlank(data.secteur(), data.activite());
        BankAccountData bankAccount = parseBankAccount(data.rib());
        String html = loadResourceAsString("contrats/contrat_affiliation_ecommerce.html");

        html = replaceTemplateToken(html, "creationCheck", templateCheckbox(true));
        html = replaceTemplateToken(html, "avenantCheck", templateCheckbox(false));
        html = replaceTemplateToken(html, "merchantNumber", templateValue(firstNotBlank(data.merchantNumber(), data.reference())));
        html = replaceTemplateToken(html, "pointOfSaleReference", templateValue(data.pointOfSaleReference()));
        html = replaceTemplateToken(html, "mcc", templateValue(data.mcc()));
        html = replaceTemplateToken(html, "raisonSociale", templateValue(firstNotBlank(data.raisonSociale(), data.entityName())));
        html = replaceTemplateToken(html, "formeJuridique", templateValue(data.formeJuridique()));
        html = replaceTemplateToken(html, "secteurActivite", templateValue(activityOrSector));
        html = replaceTemplateToken(html, "enseigne", templateValue(data.enseigne()));
        html = replaceTemplateToken(html, "adresse", templateValue(data.adresse()));
        html = replaceTemplateToken(html, "quartier", templateValue(data.quartier()));
        html = replaceTemplateToken(html, "codePostal", templateValue(data.codePostal()));
        html = replaceTemplateToken(html, "ville", templateValue(data.ville()));
        html = replaceTemplateToken(html, "ice", templateValue(data.ice()));
        html = replaceTemplateToken(html, "rc", templateValue(data.rc()));
        html = replaceTemplateToken(html, "patente", templateValue(data.patente()));
        html = replaceTemplateToken(html, "representantLegal", templateValue(legalRepresentative));
        html = replaceTemplateToken(html, "fonction", templateValue(data.fonction()));
        html = replaceTemplateToken(html, "telephoneFixe", templateValue(data.telephoneSecondaire()));
        html = replaceTemplateToken(html, "gsm", templateValue(firstNotBlank(data.telephonePrincipal(), data.telephoneSecondaire())));
        html = replaceTemplateToken(html, "emailReleve", templateValue(data.email()));
        html = replaceTemplateToken(html, "beneficiairesEffectifs", templateValue(data.beneficiairesEffectifs()));
        html = replaceTemplateToken(html, "cin", templateValue(data.cin()));
        html = replaceTemplateToken(html, "dateNaissance", templateValue(data.dateNaissance()));
        html = replaceTemplateToken(html, "nationalite", templateValue(data.nationalite()));
        html = replaceTemplateToken(html, "integrationSiteCheck", templateCheckbox(hasMode(mode, "SiteMarchand")));
        html = replaceTemplateToken(html, "integrationMobileCheck", templateCheckbox(hasMode(mode, "ApplicationMobile")));
        html = replaceTemplateToken(html, "payByLinkManuelCheck", templateCheckbox(hasMode(mode, "PayByLinkManuel")));
        html = replaceTemplateToken(html, "payByLinkAutomatiqueCheck", templateCheckbox(hasMode(mode, "PayByLinkAutomatique")));
        html = replaceTemplateToken(html, "siteMarchandUrl", templateValue(data.siteMarchandUrl()));
        html = replaceTemplateToken(html, "applicationMobile", templateValue(data.applicationMobile()));
        html = replaceTemplateToken(html, "commissionLocaleEcommerce", templateValue(data.commissionLocaleEcommerce()));
        html = replaceTemplateToken(html, "commissionEtrangereEcommerce", templateValue(data.commissionEtrangereEcommerce()));
        html = replaceTemplateToken(html, "fraisMiseEnServiceEcommerce", templateValue(data.fraisMiseEnServiceEcommerce()));
        html = replaceTemplateToken(html, "bankCodeCells", ribTemplateCells(bankAccount.bankCode(), 3));
        html = replaceTemplateToken(html, "cityCodeCells", ribTemplateCells(bankAccount.cityCode(), 3));
        html = replaceTemplateToken(html, "accountNumberCells", ribTemplateCells(bankAccount.accountNumber(), 16));
        html = replaceTemplateToken(html, "ribKeyCells", ribTemplateCells(bankAccount.ribKey(), 2));
        html = replaceTemplateToken(html, "currencyCells", ribTemplateCells(bankAccount.currency(), 3));
        html = replaceTemplateToken(html, "faitAVille", templateValue(data.ville()));
        html = replaceTemplateToken(html, "contractDate", templateValue(data.dateLabel()));
        html = replaceTemplateToken(html, "logoUrl", escapeHtml(data.logoUrl()));
        html = replaceTemplateToken(html, "visaLogoUrl", escapeHtml(data.visaLogoUrl()));
        html = replaceTemplateToken(html, "mastercardLogoUrl", escapeHtml(data.mastercardLogoUrl()));
        html = replaceTemplateToken(html, "unionPayLogoUrl", escapeHtml(data.unionPayLogoUrl()));
        html = replaceTemplateToken(html, "marocPayLogoUrl", escapeHtml(data.marocPayLogoUrl()));
        return html;
    }

    private String replaceTemplateToken(String template, String token, String value) {
        return template.replace("{{" + token + "}}", Objects.requireNonNullElse(value, ""));
    }

    private String templateCheckbox(boolean checked) {
        return checked ? "&#10003;" : "&#160;";
    }

    private String templateValue(String value) {
        return formValue(value);
    }

    private String ribTemplateCells(String value, int boxCount) {
        String compactValue = Objects.requireNonNullElse(value, "")
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < boxCount; index++) {
            String cellValue = index < compactValue.length()
                ? escapeHtml(String.valueOf(compactValue.charAt(index)))
                : "&#160;";
            builder.append("<div class=\"rib-c\">").append(cellValue).append("</div>");
        }

        return builder.toString();
    }

    private String inlineField(String label, String value) {
        return """
            <table class="line-item" role="presentation">
              <tr>
                <td class="line-label">%s</td>
                <td class="line-value">%s</td>
              </tr>
            </table>
            """.formatted(escapeHtml(label), formValue(value));
    }

    private String checkboxOption(String label, boolean checked) {
        return """
            <span class="check-option"><span class="check-mark">%s</span>%s</span>
            """.formatted(checkbox(checked), escapeHtml(label));
    }

    private String formValue(String value) {
        String normalizedValue = normalizeDisplayValue(value);
        return StringUtils.hasText(normalizedValue) ? escapeHtml(normalizedValue) : "-";
    }

    private boolean hasMode(String mode, String expectedValue) {
        String normalizedMode = compactToken(mode);
        String normalizedExpectedValue = compactToken(expectedValue);
        if (!StringUtils.hasText(normalizedMode) || !StringUtils.hasText(normalizedExpectedValue)) {
            return false;
        }

        if (normalizedMode.equals(normalizedExpectedValue)) {
            return true;
        }

        for (String token : Objects.requireNonNullElse(mode, "").split("[,;/|]")) {
            if (compactToken(token).equals(normalizedExpectedValue)) {
                return true;
            }
        }

        return false;
    }

    private String compactToken(String value) {
        return Objects.requireNonNullElse(value, "")
            .replaceAll("[^A-Za-z0-9]", "")
            .toLowerCase(Locale.ROOT);
    }

    private String bankAccountBoxes(String rib) {
        BankAccountData bankAccount = parseBankAccount(rib);
        return """
            <table class="bank-table" role="presentation">
              <tr>
                %s
                %s
                %s
                %s
                %s
              </tr>
            </table>
            """.formatted(
            bankAccountGroup("Code Banque", bankAccount.bankCode(), 3, "13%"),
            bankAccountGroup("Code Ville", bankAccount.cityCode(), 3, "13%"),
            bankAccountGroup("Numéro de compte", bankAccount.accountNumber(), 16, "44%"),
            bankAccountGroup("Cle RIB", bankAccount.ribKey(), 2, "10%"),
            bankAccountGroup("Devise", bankAccount.currency(), 3, "20%")
        );
    }

    private String bankAccountGroup(String label, String value, int boxCount, String width) {
        return """
            <td style="width: %s;">
              <table class="box-table" role="presentation">
                <tr>%s</tr>
              </table>
              <div class="bank-label">%s</div>
            </td>
            """.formatted(width, bankAccountCells(value, boxCount), escapeHtml(label));
    }

    private String bankAccountCells(String value, int boxCount) {
        String compactValue = Objects.requireNonNullElse(value, "")
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < boxCount; index++) {
            String cellValue = index < compactValue.length()
                ? escapeHtml(String.valueOf(compactValue.charAt(index)))
                : "&#160;";
            builder.append("<td class=\"char-box\">").append(cellValue).append("</td>");
        }

        return builder.toString();
    }

    private BankAccountData parseBankAccount(String rib) {
        String compactRib = Objects.requireNonNullElse(rib, "")
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT);
        return new BankAccountData(
            sliceValue(compactRib, 0, 3),
            sliceValue(compactRib, 3, 6),
            sliceValue(compactRib, 6, 22),
            sliceValue(compactRib, 22, 24),
            sliceValue(compactRib, 24, 27)
        );
    }

    private String sliceValue(String value, int startInclusive, int endExclusive) {
        if (!StringUtils.hasText(value) || startInclusive >= value.length()) {
            return "";
        }
        return value.substring(startInclusive, Math.min(endExclusive, value.length()));
    }

    private String fieldCell(String label, String value, String fallback) {
        return """
            <td class="field-cell">
              <span class="field-label">%s</span>
              <span class="field-value">%s</span>
            </td>
            """.formatted(escapeHtml(label), fillValue(value, fallback));
    }

    private String checkbox(boolean checked) {
        return checked ? "X" : "&#160;";
    }

    private String fillValue(String value, String fallback) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        return escapeHtml(StringUtils.hasText(resolved) ? resolved.trim() : "-");
    }

    private String multilineTemplateValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "&#160;";
        }

        return escapeHtml(value.trim()).replace("\n", "<br/>");
    }

    private String commercialReportWideValue(
        String value,
        CommercialReportLayoutMode layoutMode
    ) {
        return multilineTemplateValue(
            truncateText(value, layoutMode.maxWideFieldCharacters())
        );
    }

    private String commercialReportCommentValue(
        String value,
        CommercialReportLayoutMode layoutMode
    ) {
        return multilineTemplateValue(
            truncateText(value, layoutMode.maxCommentCharacters())
        );
    }

    private String truncateText(String value, int maxCharacters) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String trimmedValue = value.trim();
        if (maxCharacters <= 0 || trimmedValue.length() <= maxCharacters) {
            return trimmedValue;
        }

        int safeLength = Math.max(1, maxCharacters - 3);
        return trimmedValue.substring(0, safeLength).trim() + "...";
    }

    private String loadResourceAsString(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            byte[] content = resource.getInputStream().readAllBytes();
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private String resolveAffiliationPackageLabel(dossier_affiliation dossier) {
        if (dossier.getTypeAffiliation() == null) {
            return "Standard";
        }

        return switch (dossier.getTypeAffiliation()) {
            case TPE -> firstNotBlank(dossier.getAbonnementPackage(), dossier.getModeMiseADispositionTpe(), "Standard");
            case E_COMMERCE -> firstNotBlank(dossier.getModeServiceEcommerce(), "Standard");
            case SOFTPOS, QR_CODE -> firstNotBlank(
                dossier.getAbonnementPackage(),
                dossier.getModeleQrSoftpos(),
                mapAffiliationTypeLabel(dossier.getTypeAffiliation())
            );
            case ENCAISSEMENT_ET_ECOMMERCE -> firstNotBlank(dossier.getAbonnementPackage(), "Encaissement + E-commerce");
        };
    }

    private String mapAffiliationTypeLabel(TypeAffiliation typeAffiliation) {
        if (typeAffiliation == null) {
            return "";
        }

        return switch (typeAffiliation) {
            case TPE -> "TPE";
            case E_COMMERCE -> "E-commerce";
            case SOFTPOS -> "SoftPOS";
            case QR_CODE -> "QR Code";
            case ENCAISSEMENT_ET_ECOMMERCE -> "Encaissement + E-commerce";
        };
    }

    private String mapMerchantTypeLabel(TypeCommercant typeCommercant) {
        if (typeCommercant == null) {
            return "";
        }

        return switch (typeCommercant) {
            case PERSONNE_PHYSIQUE -> "Personne physique";
            case PERSONNE_MORALE -> "Personne morale";
            case AUTO_ENTREPRENEUR -> "Auto-entrepreneur";
            case ASSOCIATION_FONDATION -> "Association / Fondation";
        };
    }

    private String resolveCommercialName(dossier_affiliation dossier) {
        if (dossier == null || dossier.getCommerciale() == null) {
            return "";
        }

        return firstNotBlank(
            String.join(
                " ",
                safe(dossier.getCommerciale().getPrenom()),
                safe(dossier.getCommerciale().getNom())
            ).trim(),
            dossier.getCommerciale().getUtilisateur() == null
                ? ""
                : safe(dossier.getCommerciale().getUtilisateur().getEmail())
        );
    }

    private boolean matchesOption(String value, String... expectedValues) {
        String normalizedValue = compactToken(value);
        if (!StringUtils.hasText(normalizedValue) || expectedValues == null) {
            return false;
        }

        for (String expectedValue : expectedValues) {
            if (normalizedValue.equals(compactToken(expectedValue))) {
                return true;
            }
        }

        return false;
    }

    private String formatOptionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        try {
            return DATE_FORMATTER.format(LocalDate.parse(value.trim()));
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private String formatPercent(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalizedValue = value.trim().replace("%", "").trim();
        return normalizedValue.isEmpty() ? "" : normalizedValue + "%";
    }

    private String fullName(String prenom, String nom) {
        return firstNotBlank(String.join(" ", safe(prenom), safe(nom)).trim());
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String escapeHtml(String value) {
        return Objects.requireNonNullElse(value, "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String safe(Object value) {
        return normalizeDisplayValue(value == null ? null : String.valueOf(value));
    }

    private String field(String value) {
        return safe(value);
    }

    private String normalizeDisplayValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String trimmedValue = value.trim();
        return switch (trimmedValue.toLowerCase(Locale.ROOT)) {
            case "null", "undefined" -> "";
            default -> trimmedValue;
        };
    }

    private record MerchantProfile(
        String entityName,
        String raisonSociale,
        String signatoryName,
        String formeJuridique,
        String rc,
        String ice,
        String cin,
        String representantLegal
    ) {
        private static MerchantProfile empty() {
            return new MerchantProfile("", "", "", "", "", "", "", "");
        }
    }

    private record BankAccountData(
        String bankCode,
        String cityCode,
        String accountNumber,
        String ribKey,
        String currency
    ) {
    }

    private record DonneesModeleContrat(
        String reference,
        String dateLabel,
        String logoUrl,
        String visaLogoUrl,
        String mastercardLogoUrl,
        String discoverLogoUrl,
        String dinerLogoUrl,
        String unionPayLogoUrl,
        String marocPayLogoUrl,
        String merchantNumber,
        String pointOfSaleReference,
        String entityName,
        String enseigne,
        String signatoryName,
        String merchantTypeLabel,
        String affiliationTypeLabel,
        String affiliationPackageLabel,
        String activite,
        String secteur,
        String mcc,
        String adresse,
        String quartier,
        String ville,
        String codePostal,
        String region,
        String email,
        String telephonePrincipal,
        String telephoneSecondaire,
        String raisonSociale,
        String formeJuridique,
        String rc,
        String ice,
        String cin,
        String representantLegal,
        String patente,
        String fonction,
        String beneficiairesEffectifs,
        String dateNaissance,
        String nationalite,
        String rib,
        String modeServiceEcommerce,
        String siteMarchandUrl,
        String applicationMobile,
        String commissionLocaleEcommerce,
        String commissionEtrangereEcommerce,
        String fraisMiseEnServiceEcommerce,
        String modeMiseADispositionTpe,
        String nombreTpe,
        String equipementTpe,
        String connectiviteTpe,
        String commissionLocaleTpe,
        String commissionEtrangereTpe,
        String depotTpe,
        String prixAchatTpe,
        String prixLicenceTpe,
        String modeleQrSoftpos,
        String commissionLocaleQrSoftpos,
        String commissionEtrangereQrSoftpos,
        String fraisServiceQrSoftpos,
        String conditionsQrSoftpos,
        boolean serviceCreditVoucher,
        boolean serviceAnnulation,
        boolean serviceDcc,
        boolean servicePreAutorisationCartePresente,
        boolean servicePreAutorisationCartePresenteConfirmationManuelle,
        boolean servicePreAutorisationManuelleConfirmationCartePresente,
        boolean serviceTransactionManuelle,
        boolean serviceTransactionManuelleSansCvv
    ) {
    }

    private record DonneesCompteRenduCommercial(
        String logoUrl,
        String qualification,
        String acquereur,
        String origineProspect,
        String origineProspectDetail,
        String contactNomPrenom,
        String contactFonction,
        String pointVenteAcronyme,
        String actionnaires,
        String commercant,
        String chaine,
        String relationsLc,
        String dateOuverture,
        String nombreEmployes,
        String activite,
        String mcc,
        String standingMagasin,
        String natureMarchandises,
        String superficieLocal,
        String statutLocal,
        String chiffreAffairesAnnuel,
        String partPaiementCarte,
        String partCarteLocale,
        String profilCommercant,
        String appreciationVisite,
        String commentaire,
        String faitA,
        String dateVisite,
        String commercialNom
    ) {
    }

    public enum CommercialReportLayoutMode {
        STANDARD("", "12mm", 220, 320),
        COMPACT("compact", "9mm", 170, 240),
        ULTRA_COMPACT("ultra-compact", "7mm", 130, 170),
        ONE_PAGE("one-page", "5mm", 92, 115);

        private final String cssClass;
        private final String pageMargin;
        private final int maxWideFieldCharacters;
        private final int maxCommentCharacters;

        CommercialReportLayoutMode(
            String cssClass,
            String pageMargin,
            int maxWideFieldCharacters,
            int maxCommentCharacters
        ) {
            this.cssClass = cssClass;
            this.pageMargin = pageMargin;
            this.maxWideFieldCharacters = maxWideFieldCharacters;
            this.maxCommentCharacters = maxCommentCharacters;
        }

        public String cssClass() {
            return cssClass;
        }

        public String pageMargin() {
            return pageMargin;
        }

        public int maxWideFieldCharacters() {
            return maxWideFieldCharacters;
        }

        public int maxCommentCharacters() {
            return maxCommentCharacters;
        }
    }
}
