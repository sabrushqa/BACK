package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.StaffAffiliationOverviewResponse;
import com.example.demo.entities.AE;
import com.example.demo.entities.Association;
import com.example.demo.entities.PM;
import com.example.demo.entities.PP;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeCommercant;
import com.example.demo.repositories.AERepository;
import com.example.demo.repositories.AssociationRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PMRepository;
import com.example.demo.repositories.PPRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie que la vue d'ensemble des dossiers (getRequests, utilisee par le
 * back-office et le superviseur) resout correctement le profil metier du
 * commerçant pour chacun des quatre types (PP, PM, AE, Association).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class StaffAffiliationOverviewMerchantProfileTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PPRepository ppRepository;

    @Autowired
    private PMRepository pmRepository;

    @Autowired
    private AERepository aeRepository;

    @Autowired
    private AssociationRepository associationRepository;

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

    private commercant newCommercant(String name, TypeCommercant type) {
        utilisateur merchantUser = persistUser(
            "merchant." + name.toLowerCase() + "@test.lanacash.ma",
            RoleUser.COMMERCANT
        );
        commercant commercant = new commercant();
        commercant.setNomCommercial(name);
        commercant.setType(type);
        commercant.setUtilisateur(merchantUser);
        return commercantRepository.save(commercant);
    }

    private void saveDossier(commercant commercant) {
        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);
    }

    @Test
    void resolvesMerchantProfileForEachCommercantType() {
        utilisateur superviseur = persistUser("superviseur.overview.profile@test.lanacash.ma", RoleUser.SUPERVISEUR);

        commercant ppCommercant = newCommercant("Boutique PP Profile", TypeCommercant.PERSONNE_PHYSIQUE);
        PP pp = new PP();
        pp.setCommercant(ppCommercant);
        pp.setNom("Alaoui");
        pp.setPrenom("Hicham");
        pp.setCin("AB123456");
        ppRepository.save(pp);
        saveDossier(ppCommercant);

        commercant pmCommercant = newCommercant("Boutique PM Profile", TypeCommercant.PERSONNE_MORALE);
        PM pm = new PM();
        pm.setCommercant(pmCommercant);
        pm.setRaisonSociale("Lana Distribution SARL");
        pm.setRegistreCommerce("RC12345");
        pm.setIce("ICE0001");
        pm.setFormeJuridique("SARL");
        pm.setRepresentantLegal("Nadia Fassi");
        pmRepository.save(pm);
        saveDossier(pmCommercant);

        commercant aeCommercant = newCommercant("Boutique AE Profile", TypeCommercant.AUTO_ENTREPRENEUR);
        AE ae = new AE();
        ae.setCommercant(aeCommercant);
        ae.setNom("Chraibi");
        ae.setPrenom("Omar");
        ae.setNumeroAutoEntrepreneur("AE9988");
        aeRepository.save(ae);
        saveDossier(aeCommercant);

        commercant associationCommercant =
            newCommercant("Boutique Association Profile", TypeCommercant.ASSOCIATION_FONDATION);
        Association association = new Association();
        association.setCommercant(associationCommercant);
        association.setNomEntite("Association Solidarite Lana");
        association.setRepresentantLegal("Samira Tazi");
        association.setObjet("Aide sociale");
        associationRepository.save(association);
        saveDossier(associationCommercant);

        StaffAffiliationOverviewResponse response = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(superviseur)
        );

        List<StaffAffiliationOverviewResponse.AffiliationRequestItem> requests = response.requests();

        assertThat(requests)
            .anyMatch(item -> "Alaoui".equals(item.nom()) && "Hicham".equals(item.prenom()));
        assertThat(requests)
            .anyMatch(item -> "Lana Distribution SARL".equals(item.raisonSociale()));
        assertThat(requests)
            .anyMatch(item -> "AE9988".equals(item.numeroAutoEntrepreneur()));
        assertThat(requests)
            .anyMatch(item -> "Association Solidarite Lana".equals(item.nomEntite()));
    }

    @Test
    void includesBackOfficeAndCommercialeDisplayNamesWhenAssigned() {
        utilisateur superviseur = persistUser("superviseur.overview.assigned@test.lanacash.ma", RoleUser.SUPERVISEUR);

        utilisateur backOfficeUser = persistUser("backoffice.overview.assigned@test.lanacash.ma", RoleUser.BACK_OFFICE);
        com.example.demo.entities.back_office backOffice = new com.example.demo.entities.back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setNom("Saidi");
        backOffice.setPrenom("Hicham");
        backOfficeRepository.save(backOffice);

        utilisateur commercialUser = persistUser("commercial.overview.assigned@test.lanacash.ma", RoleUser.COMMERCIAL);
        com.example.demo.entities.commerciale commerciale = new com.example.demo.entities.commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale.setNom("Bennani");
        commerciale.setPrenom("Youssef");
        commercialeRepository.save(commerciale);

        commercant commercant = newCommercant("Boutique Overview Assigned", TypeCommercant.PERSONNE_PHYSIQUE);
        PP pp = new PP();
        pp.setCommercant(commercant);
        pp.setNom("Ziani");
        pp.setPrenom("Rachid");
        pp.setCin("OP334455");
        ppRepository.save(pp);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setBackOffice(backOffice);
        dossier.setCommerciale(commerciale);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        StaffAffiliationOverviewResponse response = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(superviseur)
        );

        assertThat(response.requests())
            .anyMatch(item -> item.backOfficeTraitant().contains("Hicham")
                && item.commercialAttribue().contains("Youssef"));
    }

    @Autowired
    private com.example.demo.repositories.BackOfficeRepository backOfficeRepository;

    @Autowired
    private com.example.demo.repositories.CommercialeRepository commercialeRepository;
}
