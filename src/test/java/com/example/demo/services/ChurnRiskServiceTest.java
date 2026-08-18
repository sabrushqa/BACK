package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.demo.dto.SupervisorRiskOverviewResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifie la page superviseur "Risque d'abandon" : agregation des features
 * (voir lana-merchant-intelligence/features.py) a partir de l'historique
 * switch reel, appel du modele de scoring, et regroupement secteur/canal —
 * sans dependre d'un vrai service Python (ChurnModelClient mocke).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class ChurnRiskServiceTest {

    @Autowired
    private ChurnRiskService churnRiskService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @MockitoBean
    private SwitchMonetiqueClient switchMonetiqueClient;

    @MockitoBean
    private ChurnModelClient churnModelClient;

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

    private SwitchMonetiqueClient.SwitchTransaction tx(
        String id, String canal, String commercantId, double montant, String statut, String date
    ) {
        return new SwitchMonetiqueClient.SwitchTransaction(
            id, canal, "TPE-000001", null, commercantId,
            BigDecimal.valueOf(montant), "MAD", "ACHAT", statut, "PUCE",
            LocalDateTime.parse(date + "T10:00:00"),
            null, null, null, null, null, null
        );
    }

    @Test
    void computesFeaturesFromRealHistoryAndReturnsModelScore() {
        utilisateur superviseur = persistUser("superviseur.risque@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur merchantUser = persistUser("commercant.risque@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant merchant = new commercant();
        merchant.setUtilisateur(merchantUser);
        merchant.setNomCommercial("Ansari Siham");
        merchant.setSecteur("Mode");
        merchant.setRegion("Casablanca-Settat");
        merchant = commercantRepository.save(merchant);
        Long commercantId = merchant.getIdCommercant();
        String idStr = commercantId.toString();

        // Historique volontairement petit et concentre sur les 7 derniers
        // jours de la reference (2026-04-30) pour verifier des agregats
        // simples et previsibles.
        when(switchMonetiqueClient.transactions(idStr)).thenReturn(List.of(
            tx("TX-1", "TPE", idStr, 100.0, "APPROVED", "2026-04-24"),
            tx("TX-2", "TPE", idStr, 200.0, "APPROVED", "2026-04-30"),
            tx("TX-3", "TPE", idStr, 50.0, "DECLINED", "2026-04-30")
        ));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());

        when(churnModelClient.predict(any())).thenReturn(new ChurnModelClient.RiskPredictionResponse(
            commercantId, 62.5, "MOYEN", List.of("Chiffre d'affaires en baisse"), "Relance commerciale cette semaine"
        ));

        SupervisorRiskOverviewResponse response = churnRiskService.getRiskOverview("Bearer " + tokenFor(superviseur));

        assertThat(response.commercantsAnalyses()).isEqualTo(1);
        assertThat(response.commercantsIgnores()).isEqualTo(0);
        assertThat(response.commercants()).hasSize(1);

        var item = response.commercants().get(0);
        assertThat(item.commercantId()).isEqualTo(commercantId);
        assertThat(item.niveauRisque()).isEqualTo("MOYEN");
        assertThat(item.scoreRisque()).isEqualTo(62.5);
        assertThat(item.secteur()).isEqualTo("Mode");

        // Verifie que les features envoyees au modele correspondent bien a
        // l'historique reel (2 transactions approuvees = 300 MAD sur 7j,
        // 1 refusee sur 3 dans la fenetre 30j -> taux de refus 1/3).
        var captor = org.mockito.ArgumentCaptor.forClass(ChurnModelClient.MerchantFeaturesRequest.class);
        org.mockito.Mockito.verify(churnModelClient).predict(captor.capture());
        ChurnModelClient.MerchantFeaturesRequest sentFeatures = captor.getValue();
        assertThat(sentFeatures.ca7j()).isEqualTo(300.0);
        assertThat(sentFeatures.transactions7j()).isEqualTo(2);
        assertThat(sentFeatures.tauxRefus30j()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(0.001));
        assertThat(sentFeatures.secteur()).isEqualTo("Mode");

        assertThat(response.secteursRisque()).hasSize(1);
        assertThat(response.secteursRisque().get(0).secteur()).isEqualTo("Mode");
        assertThat(response.secteursRisque().get(0).scoreMoyen()).isEqualTo(62.5);
    }

    @Test
    void merchantsWithoutTransactionHistoryAreIgnoredNotCrashed() {
        utilisateur superviseur = persistUser("superviseur.risque2@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur merchantUser = persistUser("commercant.sanshistorique@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant merchant = new commercant();
        merchant.setUtilisateur(merchantUser);
        merchant = commercantRepository.save(merchant);
        final Long commercantId = merchant.getIdCommercant();

        when(switchMonetiqueClient.transactions(commercantId.toString())).thenReturn(List.of());
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());

        SupervisorRiskOverviewResponse response = churnRiskService.getRiskOverview("Bearer " + tokenFor(superviseur));

        assertThat(response.commercants()).noneMatch(item -> item.commercantId().equals(commercantId));
        assertThat(response.commercantsIgnores()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sectorCanalBreakdownExcludesSamplesBelowFiveTransactions() {
        utilisateur superviseur = persistUser("superviseur.risque3@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur merchantUser = persistUser("commercant.echantillon@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant merchant = new commercant();
        merchant.setUtilisateur(merchantUser);
        merchant.setSecteur("Alimentation");
        merchant = commercantRepository.save(merchant);
        String idStr = merchant.getIdCommercant().toString();

        // Seulement 3 transactions ECOMMERCE pour ce secteur : sous le seuil
        // de fiabilite (5), ne doit pas apparaitre dans canalPerformance.
        when(switchMonetiqueClient.transactions(idStr)).thenReturn(List.of(
            tx("TX-A", "ECOMMERCE", idStr, 100.0, "APPROVED", "2026-04-28"),
            tx("TX-B", "ECOMMERCE", idStr, 100.0, "DECLINED", "2026-04-29"),
            tx("TX-C", "ECOMMERCE", idStr, 100.0, "APPROVED", "2026-04-30")
        ));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(churnModelClient.predict(any())).thenReturn(new ChurnModelClient.RiskPredictionResponse(
            merchant.getIdCommercant(), 10.0, "FAIBLE", List.of(), "Maintenir le suivi habituel"
        ));

        SupervisorRiskOverviewResponse response = churnRiskService.getRiskOverview("Bearer " + tokenFor(superviseur));

        assertThat(response.canalPerformance()).isEmpty();
    }

    @Test
    void nonSupervisorCannotAccessRiskOverview() {
        utilisateur commercial = persistUser("commercial.risque@test.lanacash.ma", RoleUser.COMMERCIAL);

        assertThatThrownBy(() -> churnRiskService.getRiskOverview("Bearer " + tokenFor(commercial)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
    }
}
