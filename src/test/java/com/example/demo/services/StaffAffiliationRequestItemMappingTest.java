package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.StaffAffiliationOverviewResponse;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * mapRequestItem() construit un DTO d'une centaine de champs. Les tests
 * existants ne peuplaient qu'un sous-ensemble (profil marchand, back
 * office/commerciale assignes), laissant tous les champs specifiques
 * TPE/e-commerce/QR-Softpos/compte-rendu de visite/PDV demande a leur valeur
 * par defaut (jamais exerces). Ce test peuple un dossier NOUVEAU_PDV complet
 * avec un principal accepte (pour exercer aussi le rattachement par
 * heritage commerciale/back-office) afin de lever ces branches en un seul
 * passage.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffAffiliationRequestItemMappingTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void mapsExtensionRequestWithFullDetailsInheritedFromAcceptedPrincipalDossier() {
        utilisateur superviseur = persistUser("superviseur.mapping.full@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur commercialUser = persistUser("commercial.mapping.full@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Bennani");
        commerciale.setPrenom("Youssef");
        commerciale = commercialeRepository.save(commerciale);

        utilisateur backOfficeUser = persistUser("backoffice.mapping.full@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setNom("Saidi");
        backOffice.setPrenom("Hicham");
        backOffice = backOfficeRepository.save(backOffice);

        utilisateur merchantUser = persistUser("commercant.mapping.full@test.lanacash.ma", RoleUser.COMMERCANT);
        merchantUser.setActive(false);
        merchantUser.setPasswordExpiresAt(java.time.LocalDateTime.now().plusDays(1));
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant.setNomCommercial("Boutique Mapping Full");
        commercant.setRaisonSociale("Mapping Full SARL");
        commercant.setType(TypeCommercant.PERSONNE_MORALE);
        commercant.setTelephone("0600000001");
        commercant.setTelephoneSecondaire("0600000002");
        commercant.setAdresse("12 rue Test");
        commercant.setVille("Casablanca");
        commercant.setRegion("Casablanca-Settat");
        commercant.setActivite("Commerce general");
        commercant.setSecteur("Retail");
        commercant.setMcc("5411");
        commercant.setChainePointVente("Chaine Test");
        commercant.setNombrePointsVente(3);
        commercant.setRegistreCommerce("RC99887766");
        commercant.setIdentifiantFiscal("IF12345");
        commercant = commercantRepository.save(commercant);

        // Dossier principal accepte, sert de source de rattachement par heritage
        // pour le dossier d'extension (commerciale/back-office non redefinis dessus).
        dossier_affiliation principal = new dossier_affiliation();
        principal.setCommercant(commercant);
        principal.setStatus(StatusDossier.ACCEPTE);
        principal.setTypeAffiliation(TypeAffiliation.TPE);
        principal.setCommerciale(commerciale);
        principal.setBackOffice(backOffice);
        principal.setDateSoumission(LocalDate.now().minusDays(30));
        dossierAffiliationRepository.save(principal);

        pdv requestedPdv = new pdv();
        requestedPdv.setCommercant(commercant);
        requestedPdv.setNomPDV("Point de vente demande");
        requestedPdv.setAdresse("45 avenue Extension");
        requestedPdv.setVille("Rabat");
        requestedPdv.setCodePostal("10000");
        requestedPdv.setTelephone("0600000099");
        requestedPdv.setEmail("pdv.extension@test.lanacash.ma");
        requestedPdv.setStatut("EN_ATTENTE");
        requestedPdv.setDateCreation(LocalDate.now());
        requestedPdv = pdvRepository.save(requestedPdv);

        dossier_affiliation extension = new dossier_affiliation();
        extension.setCommercant(commercant);
        extension.setOrigineCreation("NOUVEAU_PDV");
        extension.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        extension.setTypeAffiliation(TypeAffiliation.TPE);
        extension.setDateSoumission(LocalDate.now());
        extension.setRequestedPdv(requestedPdv);
        extension.setMotifRefus("Aucun motif");
        extension.setDateTraitementBackOffice(LocalDate.now());
        extension.setModeMiseADispositionTpe("VENTE");
        extension.setNombreTpe(2);
        extension.setEquipementTpe("Ingenico Move");
        extension.setConnectiviteTpe("GPRS");
        extension.setCommissionLocaleTpe("1.5");
        extension.setCommissionEtrangereTpe("2.5");
        extension.setDepotTpe("500");
        extension.setPrixAchatTpe("2000");
        extension.setPrixLicenceTpe("100");
        extension.setModeServiceEcommerce("SiteMarchand");
        extension.setSiteMarchandUrl("https://boutique-test.ma");
        extension.setApplicationMobile("com.test.app");
        extension.setCommissionLocaleEcommerce("1.8");
        extension.setCommissionEtrangereEcommerce("2.8");
        extension.setFraisMiseEnServiceEcommerce("300");
        extension.setModeleQrSoftpos("QR_STATIQUE");
        extension.setCommissionLocaleQrSoftpos("1.2");
        extension.setCommissionEtrangereQrSoftpos("2.2");
        extension.setFraisServiceQrSoftpos("150");
        extension.setConditionsQrSoftpos("Conditions specifiques");
        extension.setServiceCreditVoucher(true);
        extension.setServiceAnnulation(true);
        extension.setServiceDcc(true);
        extension.setServicePreAutorisationCartePresente(true);
        extension.setServicePreAutorisationCartePresenteConfirmationManuelle(true);
        extension.setServicePreAutorisationManuelleConfirmationCartePresente(true);
        extension.setServiceTransactionManuelle(true);
        extension.setServiceTransactionManuelleSansCvv(true);
        extension.setCompteRenduQualification("Qualifie");
        extension.setCompteRenduAcquereur("Acquereur Test");
        extension.setCompteRenduOrigineProspect("Salon");
        extension.setCompteRenduOrigineProspectDetail("Salon commerce 2026");
        extension.setCompteRenduContactNomPrenom("Ali Test");
        extension.setCompteRenduContactFonction("Gerant");
        extension.setCompteRenduPointVenteAcronyme("PVT");
        extension.setCompteRenduActionnaires("Actionnaire Test");
        extension.setCompteRenduCommercant("Commercant Test");
        extension.setCompteRenduChaine("Chaine Test");
        extension.setCompteRenduRelationsLc("Bonnes relations");
        extension.setCompteRenduDateOuverture("2020-01-01");
        extension.setCompteRenduNombreEmployes("5");
        extension.setCompteRenduActivite("Commerce");
        extension.setCompteRenduMcc("5411");
        extension.setCompteRenduStandingMagasin("Moyen");
        extension.setCompteRenduNatureMarchandises("Alimentation");
        extension.setCompteRenduSuperficieLocal("80m2");
        extension.setCompteRenduStatutLocal("Locataire");
        extension.setCompteRenduChiffreAffairesAnnuel("500000");
        extension.setCompteRenduPartPaiementCarte("40%");
        extension.setCompteRenduPartCarteLocale("60%");
        extension.setCompteRenduProfilCommercant("Serieux");
        extension.setCompteRenduAppreciationVisite("Positive");
        extension.setCompteRenduCommentaire("RAS");
        extension.setCompteRenduFaitA("Casablanca");
        extension.setCompteRenduDateVisite("2026-07-01");
        dossierAffiliationRepository.save(extension);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());

        StaffAffiliationOverviewResponse response = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(superviseur)
        );

        List<StaffAffiliationOverviewResponse.AffiliationRequestItem> items = response.requests();
        StaffAffiliationOverviewResponse.AffiliationRequestItem item = items.stream()
            .filter(it -> it.dossierId().equals(extension.getIdDossier()))
            .findFirst()
            .orElseThrow();

        assertThat(item.nomCommercant()).isEqualTo("Boutique Mapping Full");
        assertThat(item.commercialAttribue()).contains("Youssef");
        assertThat(item.backOfficeTraitant()).contains("Hicham");
        assertThat(item.dossierPrincipalId()).isEqualTo(principal.getIdDossier());
        assertThat(item.nombreDemandesExtention()).isEqualTo(1);
        assertThat(item.requestedPdvNom()).isEqualTo("Point de vente demande");
        assertThat(item.requestedPdvVille()).isEqualTo("Rabat");
        assertThat(item.compteActif()).isFalse();
        assertThat(item.activationEmailSent()).isTrue();
        assertThat(item.serviceDcc()).isTrue();
        assertThat(item.modeServiceEcommerce()).isEqualTo("SiteMarchand");
        assertThat(item.compteRenduQualification()).isEqualTo("Qualifie");
    }

    /**
     * Avant le correctif, isTpeAlreadyFullyAssigned() appelait
     * switchMonetiqueClient.stockComplet() UNE FOIS PAR DOSSIER pour calculer
     * un simple booleen — avec des centaines de dossiers, autant de
     * round-trips HTTP synchrones vers switch-monetique-service, la cause
     * principale de lenteur de cet endpoint. Ce test prouve qu'un seul appel
     * suffit desormais, quel que soit le nombre de dossiers a mapper.
     */
    @Test
    void fetchesOracleTpeStockOnlyOnceRegardlessOfHowManyDossiersAreListed() {
        utilisateur superviseur = persistUser("superviseur.mapping.oracleonce@test.lanacash.ma", RoleUser.SUPERVISEUR);

        for (int i = 1; i <= 5; i++) {
            utilisateur merchantUser = persistUser("commercant.mapping.oracleonce" + i + "@test.lanacash.ma", RoleUser.COMMERCANT);
            commercant commercant = new commercant();
            commercant.setUtilisateur(merchantUser);
            commercant.setNomCommercial("Boutique Oracle Once " + i);
            commercant = commercantRepository.save(commercant);

            dossier_affiliation dossier = new dossier_affiliation();
            dossier.setCommercant(commercant);
            dossier.setStatus(StatusDossier.ACCEPTE);
            dossier.setTypeAffiliation(TypeAffiliation.TPE);
            dossier.setDateSoumission(LocalDate.now());
            dossierAffiliationRepository.save(dossier);
        }

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-ORACLE-ONCE-1", "999999", "1",
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        staffAffiliationManagementService.getRequests("Bearer " + tokenFor(superviseur));

        verify(switchMonetiqueClient, times(1)).stockComplet();
    }
}
