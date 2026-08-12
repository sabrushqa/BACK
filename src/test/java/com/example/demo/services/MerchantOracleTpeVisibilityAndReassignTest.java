package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.dto.MerchantTpePdvAssignmentRequest;
import com.example.demo.dto.MerchantTpePdvAssignmentResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
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
 * Verifie que les TPE affectes uniquement cote Oracle (flux BOA principal,
 * assignTpeToCommercant) apparaissent bien dans MerchantSessionResponse.tpes
 * (avant ce fix, seuls les TPE auto-provisionnes localement pour NOUVEAU_PDV
 * etaient visibles) et que le commercant peut les re-affecter a un autre de
 * ses PDV (MerchantWorkspaceManagementService::assignTpeToPdv, chemin Oracle).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantOracleTpeVisibilityAndReassignTest {

    @Autowired
    private MerchantAccessService merchantAccessService;

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    @Test
    void oracleAssignedTpeAppearsInMerchantSessionTpeList() {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("commercant.oracle.visible@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Oracle Visible");
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-ORACLE-VISIBLE-1", commercantId.toString(), pointVente.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        MerchantSessionResponse response = merchantAccessService.currentSession("Bearer " + tokenFor(merchantUser));

        assertThat(response.tpes()).hasSize(1);
        assertThat(response.tpes().get(0).id()).isEqualTo("TPE-ORACLE-VISIBLE-1");
        assertThat(response.tpes().get(0).pdv()).isEqualTo("PDV Oracle Visible");
    }

    @Test
    void merchantCanReassignOracleTpeToAnotherOwnedPdv() {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("commercant.oracle.reassign@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        pdv pdvOrigine = new pdv();
        pdvOrigine.setNomPDV("PDV Origine");
        pdvOrigine.setCommercant(commercant);
        pdvOrigine = pdvRepository.save(pdvOrigine);

        pdv pdvCible = new pdv();
        pdvCible.setNomPDV("PDV Cible");
        pdvCible.setCommercant(commercant);
        pdvCible = pdvRepository.save(pdvCible);

        String tpeId = "TPE-ORACLE-REASSIGN-1";
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(java.util.Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, commercantId.toString(), pdvOrigine.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        MerchantTpePdvAssignmentResponse response = merchantWorkspaceManagementService.assignTpeToPdv(
            "Bearer " + tokenFor(merchantUser),
            tpeId,
            new MerchantTpePdvAssignmentRequest(pdvCible.getIdPDV())
        );

        assertThat(response.tpeId()).isEqualTo(tpeId);
        assertThat(response.pdvId()).isEqualTo(pdvCible.getIdPDV());
        org.mockito.Mockito.verify(switchMonetiqueClient).mettreAJourPdv(tpeId, pdvCible.getIdPDV().toString());
    }

    @Test
    void merchantCannotReassignAnotherMerchantsOracleTpe() {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("commercant.oracle.forbidden@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pdvCible = new pdv();
        pdvCible.setNomPDV("PDV Cible Forbidden");
        pdvCible.setCommercant(commercant);
        pdvCible = pdvRepository.save(pdvCible);

        String tpeId = "TPE-ORACLE-OTHER-COMMERCANT";
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(java.util.Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, "999999", "1",
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        final commercant finalCommercant = commercant;
        final pdv finalPdvCible = pdvCible;
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            merchantWorkspaceManagementService.assignTpeToPdv(
                "Bearer " + tokenFor(merchantUser),
                tpeId,
                new MerchantTpePdvAssignmentRequest(finalPdvCible.getIdPDV())
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
