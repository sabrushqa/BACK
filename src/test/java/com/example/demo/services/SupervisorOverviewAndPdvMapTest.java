package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.SupervisorOverviewResponse;
import com.example.demo.dto.SupervisorPdvMapResponse;
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

import static org.mockito.Mockito.when;

/**
 * getOverview()/getPdvMap() n'etaient exerces qu'avec une base vide (aucun
 * back-office/commerciale/commercant/pdv), donc leurs methodes de mapping
 * (mapBackOfficeItem, mapCommercialeItem, mapCommercantItem, mapPdvMapItem)
 * n'etaient jamais executees avec de vraies donnees. Ce test peuple ces
 * entites et verifie le contenu reel des reponses.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorOverviewAndPdvMapTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

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
    void getOverviewListsBackOfficesCommercialesAndCommercants() {
        utilisateur superviseur = persistUser("superviseur.overview.data@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur backOfficeUser = persistUser("backoffice.overview.data@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setNom("Saidi");
        backOffice.setPrenom("Hicham");
        backOffice.setMatricule("BO-DATA-1");
        backOffice.setService("Conformite");
        backOfficeRepository.save(backOffice);

        utilisateur commercialUser = persistUser("commercial.overview.data@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Bennani");
        commerciale.setPrenom("Youssef");
        commerciale.setMatricule("COM-DATA-1");
        commerciale.setRegion("Casablanca-Settat");
        commercialeRepository.save(commerciale);

        utilisateur merchantUser = persistUser("commercant.overview.data@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant.setNomCommercial("Boutique Overview Data");
        commercant.setType(TypeCommercant.PERSONNE_PHYSIQUE);
        commercant.setActivite("Commerce general");
        commercant.setVille("Casablanca");
        commercant.setRegion("Casablanca-Settat");
        commercantRepository.save(commercant);

        SupervisorOverviewResponse response = supervisorManagementService.getOverview(
            "Bearer " + tokenFor(superviseur)
        );

        assertThat(response.backOffices())
            .anyMatch(item -> "Saidi".equals(item.nom()) && "Hicham".equals(item.prenom()));
        assertThat(response.commerciales())
            .anyMatch(item -> "Bennani".equals(item.nom()) && "Casablanca-Settat".equals(item.region()));
        assertThat(response.commercants())
            .anyMatch(item -> "Boutique Overview Data".equals(item.nom()));
    }

    @Test
    void getPdvMapReturnsGeolocatedActivePdvWithMerchantAndAffiliationDetails() {
        utilisateur superviseur = persistUser("superviseur.pdvmap.data@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur merchantUser = persistUser("commercant.pdvmap.data@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant.setNomCommercial("Boutique Pdv Map Data");
        commercant.setType(TypeCommercant.PERSONNE_MORALE);
        commercant.setRegion("Rabat-Sale-Kenitra");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setNomPDV("Point de vente geolocalise");
        pointVente.setVille("Rabat");
        pointVente.setStatut("ACTIF");
        pointVente.setDateCreation(LocalDate.now());
        pointVente.setLatitude(34.0209);
        pointVente.setLongitude(-6.8416);
        pdvRepository.save(pointVente);

        SupervisorPdvMapResponse response = supervisorManagementService.getPdvMap(
            "Bearer " + tokenFor(superviseur)
        );

        assertThat(response.pdvs())
            .anyMatch(item -> "Point de vente geolocalise".equals(item.nomPdv())
                && "Boutique Pdv Map Data".equals(item.nomCommercant())
                && "TPE".equals(item.typeAffiliation()));
    }

    @Test
    void getTpeStockListsAssignedTerminalWithCommercantAndPdv() {
        utilisateur superviseur = persistUser("superviseur.tpestock.data@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Tpe Stock Data");
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setNomPDV("Point de vente stock");
        pointVente = pdvRepository.save(pointVente);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-STOCK-DATA-1",
                commercant.getIdCommercant().toString(),
                pointVente.getIdPDV().toString(),
                "TPE",
                "GPRS",
                true,
                BigDecimal.ZERO,
                LocalDateTime.now()
            )
        ));

        com.example.demo.dto.SupervisorTpeStockResponse response = supervisorManagementService.getTpeStock(
            "Bearer " + tokenFor(superviseur)
        );

        assertThat(response.tpes())
            .anyMatch(item -> "TPE-STOCK-DATA-1".equals(item.numeroSerie())
                && "Boutique Tpe Stock Data".equals(item.commercant())
                && "Point de vente stock".equals(item.pdv()));
    }

    @Test
    void getPdvMapExcludesPdvWithoutCoordinatesOrInactiveStatus() {
        utilisateur superviseur = persistUser("superviseur.pdvmap.excluded@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Pdv Map Exclue");
        commercant = commercantRepository.save(commercant);

        pdv pointVenteSansCoordonnees = new pdv();
        pointVenteSansCoordonnees.setCommercant(commercant);
        pointVenteSansCoordonnees.setNomPDV("PDV sans coordonnees");
        pointVenteSansCoordonnees.setStatut("ACTIF");
        pointVenteSansCoordonnees.setDateCreation(LocalDate.now());
        pdvRepository.save(pointVenteSansCoordonnees);

        pdv pointVenteEnAttente = new pdv();
        pointVenteEnAttente.setCommercant(commercant);
        pointVenteEnAttente.setNomPDV("PDV en attente");
        pointVenteEnAttente.setStatut("EN_ATTENTE");
        pointVenteEnAttente.setDateCreation(LocalDate.now());
        pointVenteEnAttente.setLatitude(33.5731);
        pointVenteEnAttente.setLongitude(-7.5898);
        pdvRepository.save(pointVenteEnAttente);

        SupervisorPdvMapResponse response = supervisorManagementService.getPdvMap(
            "Bearer " + tokenFor(superviseur)
        );

        assertThat(response.pdvs())
            .noneMatch(item -> "PDV sans coordonnees".equals(item.nomPdv())
                || "PDV en attente".equals(item.nomPdv()));
    }
}
