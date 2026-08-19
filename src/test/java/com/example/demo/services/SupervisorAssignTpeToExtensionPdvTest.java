package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifie de bout en bout, via le vrai chemin de creation d'une demande
 * d'extension (MerchantWorkspaceManagementService::requestNewPdvProduct), que
 * l'affectation TPE (SupervisorManagementService::assignTpeToCommercant)
 * resout bien le PDV demande dans CE dossier d'extension — et pas un autre
 * PDV du meme commercant via le fallback "premier PDV trouve" — meme quand le
 * commercant possede DEJA un ou plusieurs points de vente. Repond a la
 * question : "l'affectation TPE d'une extension echoue-t-elle faute de PDV ?"
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorAssignTpeToExtensionPdvTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

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
    void assigningTpeToExtensionDossierUsesTheNewlyRequestedPdvNotTheFirstOne() {
        // Commercant deja affilie avec un PREMIER point de vente historique
        // (le plus ancien id) — c'est celui-la que le fallback "premier PDV
        // trouve" affecterait a tort si requestedPdv n'etait pas resolu.
        utilisateur merchantUser = persistUser("commercant.ext-pdv@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv premierPdv = new pdv();
        premierPdv.setNomPDV("Boutique historique");
        premierPdv.setCommercant(commercant);
        premierPdv.setStatut("ACTIF");
        pdvRepository.save(premierPdv);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(principalDossier);

        // Le commercant demande un DEUXIEME point de vente — c'est le vrai
        // chemin de production (MerchantWorkspaceManagementService), pas une
        // construction manuelle de dossier_affiliation en test.
        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "Nouvelle boutique", "45 avenue Test", "Rabat", null, null, "0600000001", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null, null,
                34.0209, -6.8416,
                null
            )
        );

        List<dossier_affiliation> dossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant());
        dossier_affiliation extensionDossier = dossiers.stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));

        assertThat(extensionDossier.getRequestedPdv()).isNotNull();
        assertThat(extensionDossier.getRequestedPdv().getIdPDV()).isNotEqualTo(premierPdv.getIdPDV());

        // Contrat signe/depose : seule etape restante, l'affectation TPE.
        extensionDossier.setStatus(StatusDossier.ACCEPTE);
        dossierAffiliationRepository.save(extensionDossier);

        utilisateur backOfficeUser = persistUser("boa.ext-pdv@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        String tpeId = "TPE-EXT-PDV-TEST-1";
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, null, null, "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(switchMonetiqueClient.affecter(any(), any(), any(), any(), any(), any())).thenReturn(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, commercant.getIdCommercant().toString(),
                extensionDossier.getRequestedPdv().getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        );

        // Ne doit PAS lever "Aucun point de vente n'est lie a ce commerçant" :
        // requestedPdv est bien resolu depuis CE dossier d'extension.
        supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            tpeId,
            new SupervisorTpeAssignRequest(extensionDossier.getIdDossier())
        );

        verify(switchMonetiqueClient).affecter(
            eq(tpeId),
            eq(commercant.getIdCommercant().toString()),
            eq(extensionDossier.getRequestedPdv().getIdPDV().toString()),
            any(), any(), any()
        );
    }
}
