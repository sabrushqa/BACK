package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.entities.AE;
import com.example.demo.entities.Association;
import com.example.demo.entities.PM;
import com.example.demo.entities.PP;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.repositories.AERepository;
import com.example.demo.repositories.AssociationRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.PMRepository;
import com.example.demo.repositories.PPRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce la generation HTML des contrats/comptes-rendus pour chaque type de
 * commercant (PP/PM/AE/Association) et chaque type d'affiliation, afin de
 * couvrir les nombreuses branches de construction du modele de contrat.
 */
@SpringBootTest
@Transactional
class GenerateurModeleContratAffiliationTest {

    @Autowired
    private GenerateurModeleContratAffiliation generateur;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private PPRepository ppRepository;

    @Autowired
    private PMRepository pmRepository;

    @Autowired
    private AERepository aeRepository;

    @Autowired
    private AssociationRepository associationRepository;

    private commercant newCommercant(TypeCommercant type, String nom) {
        commercant c = new commercant();
        c.setType(type);
        c.setNomCommercial(nom);
        c.setAdresse("12 rue Test");
        c.setVille("Casablanca");
        return commercantRepository.save(c);
    }

    private dossier_affiliation newDossier(commercant owner, TypeAffiliation typeAffiliation) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(owner);
        dossier.setTypeAffiliation(typeAffiliation);
        dossier.setDateSoumission(LocalDate.now());
        dossier.setRib("007123456789012345678901");
        dossier.setModeMiseADispositionTpe("ACHAT");
        dossier.setEquipementTpe("STANDARD");
        dossier.setConnectiviteTpe("GPRS");
        dossier.setNombreTpe(1);
        dossier.setModeServiceEcommerce("INTEGRATION_API");
        dossier.setSiteMarchandUrl("https://boutique-test.ma");
        dossier.setModeleQrSoftpos("STANDARD");
        return dossier;
    }

    @Test
    void generatesEncaissementContractForPersonnePhysique() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique PP");
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Alaoui");
        pp.setPrenom("Youssef");
        pp.setCin("AB123456");
        ppRepository.save(pp);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).contains("LANA CASH");
        assertThat(html.toLowerCase()).contains("alaoui");
    }

    /**
     * Extension sur un PDV DEJA EXISTANT : le contrat reste structurellement
     * identique (memes clauses, meme mise en page), mais porte un bandeau
     * explicite mentionnant le canal demande et le nom du point de vente —
     * sans ca, ce document ressemble a un contrat d'affiliation initial
     * plutot qu'a une extension sur un point de vente deja affilie.
     */
    @Test
    void addsExtensionBannerForExtensionOnExistingPdv() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique Extension");
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Alaoui");
        pp.setPrenom("Youssef");
        pp.setCin("AB123456");
        ppRepository.save(pp);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("Boutique Historique Ain Sebaa");
        pointVente.setCommercant(commercant);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.SOFTPOS);
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setRequestedPdvDejaExistant(true);
        dossier.setRequestedPdv(pointVente);

        String html = generateur.genererHtmlContrat(dossier, pointVente);

        assertThat(html).contains("Demande d'extension");
        assertThat(html).contains("SoftPOS");
        assertThat(html).contains("Boutique Historique Ain Sebaa");
    }

    /**
     * Symetrique : une extension sur un NOUVEAU point de vente n'a pas besoin
     * du bandeau — le contrat est deja, de fait, celui d'un point de vente
     * inedit (pas de risque de confusion avec l'affiliation initiale).
     */
    @Test
    void doesNotAddExtensionBannerForExtensionOnNewPdv() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique Extension Nouveau PDV");
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Alaoui");
        pp.setPrenom("Youssef");
        pp.setCin("AB123456");
        ppRepository.save(pp);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("Nouvelle Boutique Rabat");
        pointVente.setCommercant(commercant);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setRequestedPdvDejaExistant(false);
        dossier.setRequestedPdv(pointVente);

        String html = generateur.genererHtmlContrat(dossier, pointVente);

        assertThat(html).doesNotContain("Demande d'extension");
    }

    /**
     * Le contrat d'affiliation INITIALE (pas une extension) ne doit jamais
     * porter ce bandeau, meme si origineCreation n'est pas renseigne.
     */
    @Test
    void doesNotAddExtensionBannerForInitialAffiliation() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique Initiale");
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Alaoui");
        pp.setPrenom("Youssef");
        pp.setCin("AB123456");
        ppRepository.save(pp);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).doesNotContain("Demande d'extension");
    }

    @Test
    void generatesEncaissementContractForPersonneMorale() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_MORALE, "Boutique PM SARL");
        PM pm = new PM();
        pm.setCommercant(commercant);
        pm.setRaisonSociale("Boutique PM SARL");
        pm.setRegistreCommerce("RC12345");
        pm.setIce("ICE00012345");
        pm.setFormeJuridique("SARL");
        pm.setRepresentantLegal("Sara Bennani");
        pmRepository.save(pm);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.SOFTPOS);

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).contains("LANA CASH");
        assertThat(html).contains("Boutique PM SARL");
    }

    @Test
    void generatesEncaissementContractForAutoEntrepreneur() {
        commercant commercant = newCommercant(TypeCommercant.AUTO_ENTREPRENEUR, "Boutique AE");
        AE ae = new AE();
        ae.setCommercant(commercant);
        ae.setNom("Idrissi");
        ae.setPrenom("Karim");
        ae.setNumeroAutoEntrepreneur("AE-9988");
        aeRepository.save(ae);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.QR_CODE);

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).contains("LANA CASH");
        assertThat(html.toLowerCase()).contains("idrissi");
    }

    @Test
    void generatesEncaissementContractForAssociation() {
        commercant commercant = newCommercant(TypeCommercant.ASSOCIATION_FONDATION, "Association Test");
        Association association = new Association();
        association.setCommercant(commercant);
        association.setNomEntite("Association Test");
        association.setRepresentantLegal("Nadia Fassi");
        association.setObjet("Aide sociale");
        associationRepository.save(association);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).contains("Association Test");
    }

    @Test
    void generatesEcommerceContract() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_MORALE, "Boutique Ecommerce");
        PM pm = new PM();
        pm.setCommercant(commercant);
        pm.setRaisonSociale("Boutique Ecommerce");
        pmRepository.save(pm);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.E_COMMERCE);

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).containsIgnoringCase("e-commerce");
    }

    @Test
    void generatesEcommerceContractWithMultipleIntegrationModesChecked() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_MORALE, "Boutique Ecommerce Multi");
        PM pm = new PM();
        pm.setCommercant(commercant);
        pm.setRaisonSociale("Boutique Ecommerce Multi");
        pmRepository.save(pm);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.E_COMMERCE);
        dossier.setModeServiceEcommerce("SiteMarchand,ApplicationMobile,PayByLinkManuel");

        String html = generateur.genererHtmlContrat(dossier);

        assertThat(html).containsIgnoringCase("e-commerce");
    }

    @Test
    void generatesCombinedEncaissementAndEcommerceContracts() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_MORALE, "Boutique Combinee");
        PM pm = new PM();
        pm.setCommercant(commercant);
        pm.setRaisonSociale("Boutique Combinee");
        pmRepository.save(pm);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);

        String htmlEncaissement = generateur.genererHtmlContrat(dossier);
        String htmlEcommerce = generateur.genererHtmlContratEcommerceForce(dossier);

        assertThat(htmlEncaissement).contains("LANA CASH");
        assertThat(htmlEcommerce).containsIgnoringCase("e-commerce");
    }

    @Test
    void generatesCommercialReportForStandardDossier() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique CR");
        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);
        dossier.setIdDossier(999L);

        String html = generateur.genererHtmlCompteRenduCommercial(dossier);

        assertThat(html).isNotBlank();
    }

    @Test
    void generatesAutoAffiliationSummarySheetWithoutDocuments() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique Fiche");
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Chraibi");
        pp.setPrenom("Amine");
        pp.setCin("CD654321");
        ppRepository.save(pp);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);

        String html = generateur.genererHtmlFicheAutoAffiliation(dossier, List.of());

        assertThat(html).isNotBlank();
        assertThat(html.toLowerCase()).contains("chraibi");
    }

    @Test
    void generatesAutoAffiliationSummarySheetListingDepositedDocuments() {
        commercant commercant = newCommercant(TypeCommercant.PERSONNE_PHYSIQUE, "Boutique Fiche Documents");
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Bennani");
        pp.setPrenom("Salma");
        pp.setCin("EF998877");
        ppRepository.save(pp);

        dossier_affiliation dossier = newDossier(commercant, TypeAffiliation.TPE);

        com.example.demo.entities.documents cinDocument = new com.example.demo.entities.documents();
        cinDocument.setTypeDocument(com.example.demo.enums.TypeDocument.PIECE_IDENTITE);
        cinDocument.setCheminStockage("/uploads/dossier-1/cinDocument-12345-cin.pdf");

        com.example.demo.entities.documents ribDocument = new com.example.demo.entities.documents();
        ribDocument.setTypeDocument(com.example.demo.enums.TypeDocument.RIB);
        ribDocument.setCheminStockage("/uploads/dossier-1/ribDocument-67890-rib.pdf");

        String html = generateur.genererHtmlFicheAutoAffiliation(
            dossier,
            List.of(cinDocument, ribDocument)
        );

        assertThat(html).contains("cin.pdf");
        assertThat(html).contains("rib.pdf");
    }
}
