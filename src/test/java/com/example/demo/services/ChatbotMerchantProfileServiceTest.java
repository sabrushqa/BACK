package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.demo.dto.ChatbotMerchantProfileResponse;
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
 * Le chatbot doit savoir "avec qui il parle" avant de traiter un message :
 * un commerçant E_COMMERCE pur n'a pas de TPE au sens encaissement physique
 * (SupervisorManagementService::assignTpeToCommercant ne lui en affecte
 * jamais), donc un message "problème TPE" ne le concerne pas. Ces tests
 * verifient a la fois la resolution du merchant_id depuis le JWT (jamais
 * depuis ce que le navigateur envoie — voir ChatbotProxyController) et le
 * profil expose au chatbot (type d'affiliation, PDV, TPE local+Oracle).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class ChatbotMerchantProfileServiceTest {

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

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    private utilisateur persistMerchantUser(String email) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(RoleUser.COMMERCANT);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    @Test
    void resolveAuthenticatedCommercantIdReturnsIdForCommercant() {
        utilisateur user = persistMerchantUser("profile.commercant@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant = commercantRepository.save(commercant);

        Long resolved = merchantAccessService.resolveAuthenticatedCommercantId("Bearer " + tokenFor(user));

        assertThat(resolved).isEqualTo(commercant.getIdCommercant());
    }

    @Test
    void resolveAuthenticatedCommercantIdReturnsParentIdForSousCommercant() {
        utilisateur parentUser = persistMerchantUser("profile.parent@test.lanacash.ma");
        commercant parentCommercant = new commercant();
        parentCommercant.setUtilisateur(parentUser);
        parentCommercant = commercantRepository.save(parentCommercant);

        utilisateur subUser = new utilisateur();
        subUser.setEmail("profile.souscommercant@test.lanacash.ma");
        subUser.setRole(RoleUser.SOUS_COMMERCANT);
        subUser.setActive(true);
        subUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(subUser);

        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        sousCommercant.setCommercant(parentCommercant);
        sousCommercantRepository.save(sousCommercant);

        Long resolved = merchantAccessService.resolveAuthenticatedCommercantId("Bearer " + tokenFor(subUser));

        assertThat(resolved).isEqualTo(parentCommercant.getIdCommercant());
    }

    @Test
    void resolveAuthenticatedCommercantIdReturnsNullWithoutAuthHeader() {
        assertThat(merchantAccessService.resolveAuthenticatedCommercantId(null)).isNull();
        assertThat(merchantAccessService.resolveAuthenticatedCommercantId("Bearer invalid-token")).isNull();
    }

    @Test
    void profileForTpeCommercantMarksSupportsTpeOnly() {
        utilisateur user = persistMerchantUser("profile.tpe@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Profil TPE");
        pointVente.setVille("Casablanca");
        pointVente.setAdresse("12 rue du Test");
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
                "TPE-PROFILE-1", commercantId.toString(), pointVente.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        ChatbotMerchantProfileResponse profile = merchantAccessService.getMerchantProfileForChatbot(commercantId, null);

        assertThat(profile).isNotNull();
        assertThat(profile.typeAffiliation()).isEqualTo("TPE");
        assertThat(profile.supportsTpe()).isTrue();
        assertThat(profile.supportsEcommerce()).isFalse();
        assertThat(profile.pdvs()).hasSize(1);
        assertThat(profile.pdvs().get(0).nom()).isEqualTo("PDV Profil TPE");
        assertThat(profile.tpes()).hasSize(1);
        assertThat(profile.tpes().get(0).numeroSerie()).isEqualTo("TPE-PROFILE-1");
        assertThat(profile.tpes().get(0).pdvNom()).isEqualTo("PDV Profil TPE");
    }

    @Test
    void profileForPureEcommerceCommercantHasNoTpeSupport() {
        utilisateur user = persistMerchantUser("profile.ecommerce@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());

        ChatbotMerchantProfileResponse profile =
            merchantAccessService.getMerchantProfileForChatbot(commercant.getIdCommercant(), null);

        assertThat(profile).isNotNull();
        assertThat(profile.typeAffiliation()).isEqualTo("E_COMMERCE");
        assertThat(profile.supportsTpe()).isFalse();
        assertThat(profile.supportsEcommerce()).isTrue();
        assertThat(profile.pdvs()).isEmpty();
        assertThat(profile.tpes()).isEmpty();
    }

    @Test
    void profileForCombinedAffiliationSupportsBoth() {
        utilisateur user = persistMerchantUser("profile.combined@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());

        ChatbotMerchantProfileResponse profile =
            merchantAccessService.getMerchantProfileForChatbot(commercant.getIdCommercant(), null);

        assertThat(profile.supportsTpe()).isTrue();
        assertThat(profile.supportsEcommerce()).isTrue();
    }

    @Test
    void profileReturnsNullForUnknownMerchant() {
        assertThat(merchantAccessService.getMerchantProfileForChatbot(987654321L, null)).isNull();
    }

    @Test
    void resolveAuthenticatedPdvIdForSousCommercantReturnsNullForRegularCommercant() {
        utilisateur user = persistMerchantUser("profile.pdv-scope.regular@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercantRepository.save(commercant);

        assertThat(merchantAccessService.resolveAuthenticatedPdvIdForSousCommercant("Bearer " + tokenFor(user)))
            .isNull();
    }

    @Test
    void resolveAuthenticatedPdvIdForSousCommercantReturnsAssignedPdv() {
        utilisateur parentUser = persistMerchantUser("profile.pdv-scope.parent@test.lanacash.ma");
        commercant parentCommercant = new commercant();
        parentCommercant.setUtilisateur(parentUser);
        parentCommercant = commercantRepository.save(parentCommercant);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV du sous-commerçant");
        pointVente.setCommercant(parentCommercant);
        pointVente = pdvRepository.save(pointVente);

        utilisateur subUser = new utilisateur();
        subUser.setEmail("profile.pdv-scope.sub@test.lanacash.ma");
        subUser.setRole(RoleUser.SOUS_COMMERCANT);
        subUser.setActive(true);
        subUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(subUser);

        sous_commercant sousCommercant = new sous_commercant();
        sousCommercant.setUtilisateur(subUser);
        pointVente.setSousCommercant(sousCommercant);
        pdvRepository.save(pointVente);
        sousCommercantRepository.save(sousCommercant);

        Long resolved = merchantAccessService.resolveAuthenticatedPdvIdForSousCommercant("Bearer " + tokenFor(subUser));

        assertThat(resolved).isEqualTo(pointVente.getIdPDV());
    }

    /**
     * Reproduit le scenario du bug chatbot corrige : un commercant possede
     * DEUX PDV, chacun avec son propre TPE — quand pdvId est fourni (cas
     * sous-commerçant), le profil ne doit exposer QUE le PDV/TPE vise, pas
     * les deux.
     */
    @Test
    void profileScopedToPdvIdOnlyIncludesThatPdvAndItsTpe() {
        utilisateur user = persistMerchantUser("profile.pdv-scope.multi@test.lanacash.ma");
        commercant commercant = new commercant();
        commercant.setUtilisateur(user);
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        pdv premierPdv = new pdv();
        premierPdv.setNomPDV("PDV historique");
        premierPdv.setVille("Casablanca");
        premierPdv.setCommercant(commercant);
        premierPdv = pdvRepository.save(premierPdv);

        pdv deuxiemePdv = new pdv();
        deuxiemePdv.setNomPDV("PDV du sous-commerçant");
        deuxiemePdv.setVille("Rabat");
        deuxiemePdv.setCommercant(commercant);
        deuxiemePdv = pdvRepository.save(deuxiemePdv);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(dossier);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-PDV-1", commercantId.toString(), premierPdv.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            ),
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-PDV-2", commercantId.toString(), deuxiemePdv.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        ChatbotMerchantProfileResponse profile =
            merchantAccessService.getMerchantProfileForChatbot(commercantId, deuxiemePdv.getIdPDV());

        assertThat(profile.pdvs()).hasSize(1);
        assertThat(profile.pdvs().get(0).nom()).isEqualTo("PDV du sous-commerçant");
        assertThat(profile.tpes()).hasSize(1);
        assertThat(profile.tpes().get(0).numeroSerie()).isEqualTo("TPE-PDV-2");
    }
}
