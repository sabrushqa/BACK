package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.AffiliationActivationRequest;
import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.example.demo.security.TestJwtSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Rejoue le cycle COMPLET signale manuellement, sans sauter aucune etape
 * (contrairement a SupervisorNotifiesResponsibleBackOfficeForExtensionTest
 * qui posait EN_ATTENTE_VALIDATION_BOA a la main) : requestNewPdvProduct
 * (soraya) -> completeMerchantDossier (commerciale) -> reviewMerchantDossier
 * (BOA valide) -> finalizeAutomaticAcceptance (contrat signe depose) ->
 * getRequests() + getEligibleTpesForDossier avec le MEME compte BOA qui a
 * valide, pour un dossier TPE (pas e-commerce, pour couvrir l'autre cas).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorFullExtensionLifecycleTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private com.example.demo.repositories.PdvRepository pdvRepository;

    @Autowired
    private com.example.demo.repositories.TpeRepository tpeRepository;

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
    void fullExtensionLifecycleKeepsSameBackOfficeVisibleThroughout() {
        // Stock switch VIDE : isole ce test de tout appel reseau reel vers
        // switch-monetique-service (evite tout faux positif/negatif du au
        // vrai service tournant en parallele — ce test verifie le comptage
        // LOCAL, pas Oracle).
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(switchMonetiqueClient.stockDisponible(org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of());

        // Le compte "soraya" : deja affilie TPE, avec un commercial et un BOA
        // deja rattaches a son dossier principal (cas courant).
        utilisateur merchantUser = persistUser("soraya.full@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur commercialUser = persistUser("commerciale.soraya@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        utilisateur boaUser = persistUser("boa.soraya.full@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        boa = backOfficeRepository.save(boa);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setCommerciale(commerciale);
        principalDossier.setBackOffice(boa);
        principalDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(principalDossier);

        // 1) Soraya demande une extension TPE — chemin reel.
        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "PDV Soraya 2", "10 rue Extension", "Fes", null, null, "0600000010", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null,
                34.0331, -5.0003,
                null
            )
        );

        List<dossier_affiliation> dossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant());
        dossier_affiliation extensionDossier = dossiers.stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));
        Long dossierId = extensionDossier.getIdDossier();

        assertThat(extensionDossier.getStatus()).isEqualTo(StatusDossier.SOUMIS);

        // 2) La commerciale complete la demande — chemin reel, jamais teste
        // dans les autres verifications de ce scenario.
        staffAffiliationManagementService.completeMerchantDossier(
            "Bearer " + tokenFor(commercialUser),
            dossierId,
            new AffiliationActivationRequest(
                "5411", "1.5%", "2.5%", "500", "1000", "200", "Standard",
                null, null, null, null, null, null, null,
                true, true, true, true, true, true, true, true,
                "NON_AFFILIE", null, "PROSPECTION", "Salon professionnel",
                "Jane Doe", "Gerante", "BTQ01", "Boutique Test", "Actionnaires Test",
                "Boutique Test SARL", null, "01/01/2020", "5", "Commerce general",
                "5411", "Standard", "Vente de produits", "50m2", "Proprietaire",
                "500000", "60%", "40%", "Fiable", "Bonne tenue", "RAS",
                "Casablanca", "01/01/2026"
            )
        );

        dossier_affiliation afterCompletion = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(afterCompletion.getStatus()).isEqualTo(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        assertThat(afterCompletion.getBackOffice()).isNotNull();
        assertThat(afterCompletion.getBackOffice().getIdBackOffice()).isEqualTo(boa.getIdBackOffice());

        // 3) Le BOA valide — chemin reel.
        staffAffiliationManagementService.reviewMerchantDossier(
            "Bearer " + tokenFor(boaUser),
            dossierId,
            new AffiliationReviewRequest("ACCEPTE", null)
        );

        dossier_affiliation afterApproval = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(afterApproval.getStatus()).isEqualTo(StatusDossier.CONTRAT_A_SIGNER);
        assertThat(afterApproval.getBackOffice().getIdBackOffice()).isEqualTo(boa.getIdBackOffice());

        // 4) Soraya depose le contrat signe.
        staffAffiliationManagementService.finalizeAutomaticAcceptance(afterApproval);

        dossier_affiliation accepted = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(StatusDossier.ACCEPTE);
        assertThat(accepted.getBackOffice().getIdBackOffice())
            .as("Le BOA responsable ne doit pas avoir change entre la validation et le depot du contrat")
            .isEqualTo(boa.getIdBackOffice());

        // 5) Le MEME compte BOA doit voir le dossier dans sa liste ET pouvoir
        // y affecter un TPE.
        var view = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(boaUser));
        var visible = view.requests().stream()
            .filter(item -> item.dossierId().equals(dossierId))
            .findFirst();
        assertThat(visible)
            .as("Le dossier d'extension de soraya doit etre visible pour le BOA qui l'a valide")
            .isPresent();
        assertThat(visible.get().status()).isEqualTo("ACCEPTE");
        assertThat(visible.get().tpeDejaAffecte()).isFalse();

        var eligibleTpes = supervisorManagementService.getEligibleTpesForDossier(
            "Bearer " + tokenFor(boaUser),
            dossierId
        );
        assertThat(eligibleTpes).isNotNull();

        // 6) Le BOA affecte reellement le TPE demande (1 seul, nombreTpe=1) —
        // le dossier doit alors disparaitre de "TPE a affecter"
        // (tpeDejaAffecte=true) tout en restant visible dans l'historique
        // (toujours ACCEPTE, needsManualAssignment cote front devient false
        // mais isBackOfficeHandledDecision reste vrai).
        String tpeId = "TPE-SORAYA-FULL-TEST-1";
        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, null, null, "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));
        when(switchMonetiqueClient.affecter(any(), any(), any(), any(), any(), any())).thenReturn(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, commercant.getIdCommercant().toString(),
                accepted.getRequestedPdv().getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        );

        supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(boaUser),
            tpeId,
            new com.example.demo.dto.SupervisorTpeAssignRequest(dossierId)
        );

        // assignTpeToCommercant() appelle affecter() (endpoint distinct de
        // stockComplet() dans ce mock) : contrairement au vrai switch-monetique-
        // service, le stub stockComplet() ne "voit" pas automatiquement cette
        // affectation — on le met a jour pour simuler l'etat reel du stock
        // apres affectation, sans quoi getRequests() relirait un stock perime.
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, commercant.getIdCommercant().toString(),
                accepted.getRequestedPdv().getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));

        var viewAfterAssignment = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(boaUser));
        var visibleAfterAssignment = viewAfterAssignment.requests().stream()
            .filter(item -> item.dossierId().equals(dossierId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Le dossier doit rester dans getRequests() (historique)"));

        assertThat(visibleAfterAssignment.status())
            .as("Le statut ACCEPTE ne change pas — c'est ce qui le garde visible dans l'historique BOA")
            .isEqualTo("ACCEPTE");
        assertThat(visibleAfterAssignment.tpeDejaAffecte())
            .as("Une fois le TPE demande reellement affecte, le dossier ne doit plus apparaitre dans "
                + "\"TPE a affecter\" (needsManualAssignment cote front se base sur ce champ)")
            .isTrue();
    }

    @Test
    void extensionIsNotHiddenWhenMerchantAlreadyHasATpeOnAnotherPdv() {
        // Reproduit precisement le bug constate manuellement : soraya a DEJA
        // un TPE affecte sur son PREMIER point de vente (issu de son
        // affiliation initiale) — sa NOUVELLE extension (deuxieme PDV) ne
        // doit pas etre marquee "TPE deja affecte" a cause de ce premier TPE
        // qui ne la concerne pas.
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());

        utilisateur merchantUser = persistUser("soraya.dejaaffecte@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur boaUser = persistUser("boa.soraya.dejaaffecte@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        boa = backOfficeRepository.save(boa);

        // Premier point de vente, deja equipe d'un TPE (affiliation initiale).
        com.example.demo.entities.pdv premierPdv = new com.example.demo.entities.pdv();
        premierPdv.setNomPDV("Boutique historique");
        premierPdv.setCommercant(commercant);
        premierPdv.setStatut("ACTIF");
        premierPdv = pdvRepository.save(premierPdv);

        com.example.demo.entities.tpe tpeExistant = new com.example.demo.entities.tpe();
        tpeExistant.setPdv(premierPdv);
        tpeExistant.setStatut("AFFECTE_COMMERCANT");
        tpeRepository.save(tpeExistant);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setNombreTpe(1);
        principalDossier.setBackOffice(boa);
        principalDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(principalDossier);

        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                "PDV Soraya 2", "10 rue Extension", "Fes", null, null, "0600000011", null,
                "TPE", "1", "STANDARD", "GPRS", "ACHAT", null, null, null, null,
                34.0331, -5.0003,
                null
            )
        );

        List<dossier_affiliation> dossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant());
        dossier_affiliation extensionDossier = dossiers.stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));

        extensionDossier.setStatus(StatusDossier.ACCEPTE);
        dossierAffiliationRepository.save(extensionDossier);

        var view = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(boaUser));
        var visible = view.requests().stream()
            .filter(item -> item.dossierId().equals(extensionDossier.getIdDossier()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable dans getRequests()"));

        assertThat(visible.tpeDejaAffecte())
            .as("Le TPE du PREMIER point de vente ne doit pas masquer le besoin de la NOUVELLE extension")
            .isFalse();
    }
}
