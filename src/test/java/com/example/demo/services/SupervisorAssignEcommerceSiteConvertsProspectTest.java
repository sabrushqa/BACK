package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.demo.dto.SupervisorEcommerceSiteAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equivalent, pour le canal e-commerce, de SupervisorAssignTpeConvertsProspectTest :
 * un dossier E_COMMERCE ne peut jamais recevoir de TPE (validateTpeAssignment le
 * refuse), donc CONVERTI ne peut se declencher que via l'affectation d'un site
 * e-commerce (assignEcommerceSiteToCommercant).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorAssignEcommerceSiteConvertsProspectTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

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

    private Long persistCommercialDirectDossier(String email, TypeAffiliation typeAffiliation) {
        utilisateur merchantUser = persistUser(email, RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(typeAffiliation);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setSiteMarchandUrl("https://boutique-test.example.ma");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        // L'ID n'est jamais fourni par l'appelant : switch-monetique-service
        // le genere au provisionnement (provisionnerSiteEcommerce), demo se
        // contente de le stocker tel que retourne.
        when(switchMonetiqueClient.provisionnerSiteEcommerce(any(), any())).thenReturn(
            new SwitchMonetiqueClient.SwitchSiteEcommerce(
                "ECOM-CONVERTI-TEST-1", commercant.getIdCommercant().toString(),
                "https://boutique-test.example.ma", true
            )
        );

        return dossier.getIdDossier();
    }

    @Test
    void assigningEcommerceSiteMarksCommercialDirectDossierAsConverti() {
        utilisateur backOfficeUser = persistUser("backoffice.ecom-converti@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        Long dossierId = persistCommercialDirectDossier(
            "commercant.ecom-converti@test.lanacash.ma", TypeAffiliation.E_COMMERCE
        );

        supervisorManagementService.assignEcommerceSiteToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            new SupervisorEcommerceSiteAssignRequest(dossierId, null)
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.CONVERTI);
        // L'ID stocke est bien celui genere par switch, jamais invente par demo.
        assertThat(reloaded.getIdSiteEcommerceAffecte()).isEqualTo("ECOM-CONVERTI-TEST-1");

        Long merchantUserId = reloaded.getCommercant().getUtilisateur().getId();
        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(merchantUserId)
                    && notification.getTypeNotification() == TypeNotification.SITE_ECOMMERCE_AFFECTE
            );
    }

    @Test
    void assigningEcommerceSiteToEncaissementEtEcommerceDossierIsAllowed() {
        // ENCAISSEMENT_ET_ECOMMERCE combine les deux canaux : ce type doit
        // pouvoir affecter un site e-commerce ICI (et une reference TPE via
        // assignTpeToCommercant, teste ailleurs), pas etre rejete comme un
        // dossier purement TPE.
        utilisateur backOfficeUser = persistUser("backoffice.ecom-combined@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        Long dossierId = persistCommercialDirectDossier(
            "commercant.ecom-combined@test.lanacash.ma", TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE
        );

        supervisorManagementService.assignEcommerceSiteToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            new SupervisorEcommerceSiteAssignRequest(dossierId, null)
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getIdSiteEcommerceAffecte()).isEqualTo("ECOM-CONVERTI-TEST-1");
    }

    @Test
    void assigningEcommerceSiteToTpeDossierIsRejected() {
        utilisateur backOfficeUser = persistUser("backoffice.ecom-reject@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        utilisateur merchantUser = persistUser("commercant.ecom-reject@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        Long dossierId = dossier.getIdDossier();

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> supervisorManagementService.assignEcommerceSiteToCommercant(
                "Bearer " + tokenFor(backOfficeUser),
                new SupervisorEcommerceSiteAssignRequest(dossierId, null)
            )
        );
    }
}
