package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.dto.MerchantSessionResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.sous_commercant;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.SousCommercantRepository;
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
 * Le dashboard du sous-commerçant restait a "0 transaction" malgre un
 * historique reel cote switch-monetique-service (Oracle) : buildSubMerchantSessionResponse
 * ne lisait que la table locale "transactions" (auto-provisionnement
 * NOUVEAU_PDV, quasiment toujours vide), sans jamais fusionner l'historique
 * Oracle comme le fait deja buildSessionResponse pour un commercant complet.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantAccessSubMerchantTransactionsTest {

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

    @Autowired
    private SousCommercantRepository sousCommercantRepository;

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
    void subMerchantSeesOracleTransactionsOfOwnPdvButNotOfOtherPdv() {
        utilisateur parentUser = persistUser("parent.subtx@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant parentCommercant = new commercant();
        parentCommercant.setUtilisateur(parentUser);
        parentCommercant = commercantRepository.save(parentCommercant);
        Long parentCommercantId = parentCommercant.getIdCommercant();

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(parentCommercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        pdv pdvDuSousCommercant = new pdv();
        pdvDuSousCommercant.setNomPDV("PDV du sous-commerçant");
        pdvDuSousCommercant.setCommercant(parentCommercant);
        pdvDuSousCommercant = pdvRepository.save(pdvDuSousCommercant);

        pdv autrePdv = new pdv();
        autrePdv.setNomPDV("Autre PDV du meme commercant");
        autrePdv.setCommercant(parentCommercant);
        autrePdv = pdvRepository.save(autrePdv);

        utilisateur subUser = persistUser("sub.subtx@test.lanacash.ma", RoleUser.SOUS_COMMERCANT);
        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        pdvDuSousCommercant.setSousCommercant(sousCommercant);
        pdvRepository.save(pdvDuSousCommercant);
        sousCommercantRepository.save(sousCommercant);

        // Stock Oracle : un TPE sur CHAQUE PDV du commercant.
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-SOUS-1", parentCommercantId.toString(), pdvDuSousCommercant.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            ),
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-AUTRE-1", parentCommercantId.toString(), autrePdv.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));
        // Historique Oracle du commercant PARENT (interroge par commercantId,
        // sans notion de PDV) : une transaction sur CHAQUE TPE.
        when(switchMonetiqueClient.transactions(parentCommercantId.toString())).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTransaction(
                "TXN-SOUS-1", "TPE", "TPE-SOUS-1", null, parentCommercantId.toString(),
                new BigDecimal("120.00"), "MAD", "ACHAT", "APPROVED", "ONLINE",
                LocalDateTime.now(), null, null, null, null, null, null
            ),
            new SwitchMonetiqueClient.SwitchTransaction(
                "TXN-AUTRE-1", "TPE", "TPE-AUTRE-1", null, parentCommercantId.toString(),
                new BigDecimal("50.00"), "MAD", "ACHAT", "APPROVED", "ONLINE",
                LocalDateTime.now(), null, null, null, null, null, null
            )
        ));

        MerchantSessionResponse session = merchantAccessService.currentSession("Bearer " + tokenFor(subUser));

        assertThat(session.transactions()).hasSize(1);
        assertThat(session.transactions().get(0).id()).isEqualTo("TXN-SOUS-1");
        assertThat(session.summary().totalTransactions()).isEqualTo(1);
    }
}
