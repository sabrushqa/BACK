package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantSessionResponse;
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
 * Verifie que les transactions enregistrees cote switch-monetique-service
 * (Oracle) apparaissent bien dans MerchantSessionResponse.transactions, avec
 * le bon PDV resolu via le stock TPE Oracle — c'est le chemin exact emprunte
 * par le dashboard commercant reel (GET session -> buildSessionResponse ->
 * buildTransactionItemsForCommercant). Avant ce fix, cette liste restait
 * toujours vide car SwitchMonetiqueClient n'exposait aucune methode
 * transactions().
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantOracleTransactionVisibilityTest {

    @Autowired
    private MerchantAccessService merchantAccessService;

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
    void oracleTransactionsAppearInMerchantSessionWithResolvedPdv() {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("commercant.oracle.transactions@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Beny Youness");
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        // Stock Oracle : un TPE affecte a ce commercant/PDV (necessaire pour
        // resoudre le PDV d'une transaction, qui ne porte que l'id du TPE).
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-000003", commercantId.toString(), pointVente.getIdPDV().toString(),
                "TPE", "ETHERNET", true, BigDecimal.valueOf(50000), LocalDateTime.now()
            )
        ));

        // Historique reel tel que renvoye par TransactionApiController (memes
        // champs que ceux verifies manuellement contre le switch en cours
        // d'execution : 504 transactions pour le commercant "3").
        when(switchMonetiqueClient.transactions(commercantId.toString())).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-3-000504", "TPE", "TPE-000003", null, commercantId.toString(),
                BigDecimal.valueOf(493.48), "MAD", "ACHAT", "APPROVED", "BANDE",
                LocalDateTime.parse("2026-04-30T19:14:39"),
                null, null, null, null, null, null
            ),
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-3-000001", "TPE", "TPE-000003", null, commercantId.toString(),
                BigDecimal.valueOf(610.66), "MAD", "ACHAT", "APPROVED", "NFC",
                LocalDateTime.parse("2026-01-01T09:43:47"),
                null, null, null, null, null, null
            )
        ));

        MerchantSessionResponse response = merchantAccessService.currentSession("Bearer " + tokenFor(merchantUser));

        assertThat(response.transactions()).hasSize(2);
        assertThat(response.summary().totalTransactions()).isEqualTo(2);
        // Triees par date decroissante : la plus recente (avril) doit arriver en premier.
        assertThat(response.transactions().get(0).id()).isEqualTo("TX-3-000504");
        assertThat(response.transactions().get(0).montant()).isEqualByComparingTo("493.48");
        assertThat(response.transactions().get(0).pdv()).isEqualTo("PDV Beny Youness");
        assertThat(response.transactions().get(0).pdvId()).isEqualTo(pointVente.getIdPDV());
        assertThat(response.transactions().get(1).id()).isEqualTo("TX-3-000001");
    }

    @Test
    void switchUnavailableDegradesGracefullyToEmptyTransactionList() {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail("commercant.oracle.transactions.down@test.lanacash.ma");
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        when(switchMonetiqueClient.stockComplet()).thenThrow(new IllegalStateException("switch down"));
        when(switchMonetiqueClient.transactions(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new IllegalStateException("switch down"));

        MerchantSessionResponse response = merchantAccessService.currentSession("Bearer " + tokenFor(merchantUser));

        assertThat(response.transactions()).isEmpty();
        assertThat(response.summary().totalTransactions()).isZero();
    }
}
