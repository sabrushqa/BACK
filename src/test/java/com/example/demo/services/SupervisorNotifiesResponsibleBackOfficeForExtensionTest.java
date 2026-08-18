package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.dto.MerchantPdvProductRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.utilisateur;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rejoue le scenario signale manuellement : un commercant (extension) depose
 * son contrat signe — le BOA responsable du dossier doit recevoir une alerte
 * (notification en base ; l'e-mail est un canal best-effort separe, voir
 * AffiliationStatusMailService::sendStatusUpdateEmail qui ne leve jamais
 * d'exception si le SMTP est indisponible/non configure — silencieux par
 * design, donc jamais bloquant pour la notification in-app elle-meme).
 *
 * Verifie de bout en bout, via les vraies methodes publiques (pas de dossier
 * construit a la main a mi-parcours) : requestNewPdvProduct -> (SOUMIS ->
 * EN_ATTENTE_VALIDATION_BOA, transition faite par completeMerchantDossier en
 * production, ici simulee directement car sans effet sur le point teste) ->
 * reviewMerchantDossier (BOA approuve, CONTRAT_A_SIGNER) -> finalizeAutomaticAcceptance
 * (depot du contrat signe, ACCEPTE) -> le BOA qui a approuve doit avoir une
 * notification DOSSIER_TPE_A_AFFECTER, quel que soit le type d'affiliation.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorNotifiesResponsibleBackOfficeForExtensionTest {

    @Autowired
    private MerchantWorkspaceManagementService merchantWorkspaceManagementService;

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

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
    void backOfficeThatApprovedTheExtensionIsNotifiedWhenContractIsDeposited() {
        // Compte "soraya" : deja affilie TPE, traite historiquement par un BOA precis.
        utilisateur merchantUser = persistUser("soraya@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        utilisateur responsibleBoaUser = persistUser("boa.responsable.soraya@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office responsibleBoa = new back_office();
        responsibleBoa.setUtilisateur(responsibleBoaUser);
        responsibleBoa = backOfficeRepository.save(responsibleBoa);

        dossier_affiliation principalDossier = new dossier_affiliation();
        principalDossier.setCommercant(commercant);
        principalDossier.setStatus(StatusDossier.ACCEPTE);
        principalDossier.setTypeAffiliation(TypeAffiliation.TPE);
        principalDossier.setBackOffice(responsibleBoa);
        principalDossier.setDateSoumission(LocalDate.now());
        dossierAffiliationRepository.save(principalDossier);

        // Soraya demande une extension e-commerce (chemin reel, pas de
        // dossier construit a la main).
        merchantWorkspaceManagementService.requestNewPdvProduct(
            "Bearer " + tokenFor(merchantUser),
            new MerchantPdvProductRequest(
                null, null, null, null, null, null, null,
                "E_COMMERCE", null, null, null, null, null,
                "INTEGRATION_API", "https://boutique-soraya.example.ma", null,
                null, null,
                null
            )
        );

        List<dossier_affiliation> dossiers = dossierAffiliationRepository
            .findAllByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(commercant.getIdCommercant());
        dossier_affiliation extensionDossier = dossiers.stream()
            .filter(d -> "NOUVEAU_PDV".equals(d.getOrigineCreation()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Dossier d'extension introuvable"));

        // Le BOA responsable est deja herite du dossier principal des la creation.
        assertThat(extensionDossier.getBackOffice()).isNotNull();
        assertThat(extensionDossier.getBackOffice().getIdBackOffice()).isEqualTo(responsibleBoa.getIdBackOffice());

        extensionDossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossierAffiliationRepository.save(extensionDossier);

        // Le BOA responsable approuve — vrai chemin (reviewMerchantDossier ->
        // approveDossierForContract), pas un statut pose a la main.
        staffAffiliationManagementService.reviewMerchantDossier(
            "Bearer " + tokenFor(responsibleBoaUser),
            extensionDossier.getIdDossier(),
            new AffiliationReviewRequest("ACCEPTE", null)
        );

        dossier_affiliation afterApproval = dossierAffiliationRepository.findById(extensionDossier.getIdDossier())
            .orElseThrow();
        assertThat(afterApproval.getStatus()).isEqualTo(StatusDossier.CONTRAT_A_SIGNER);
        assertThat(afterApproval.getBackOffice().getIdBackOffice()).isEqualTo(responsibleBoa.getIdBackOffice());

        // Soraya depose le contrat signe — declenche finalizeAutomaticAcceptance
        // (meme methode qu'uploadSignedContract, sans la validation du fichier
        // PDF qui n'est pas ce qu'on verifie ici).
        staffAffiliationManagementService.finalizeAutomaticAcceptance(afterApproval);

        dossier_affiliation accepted = dossierAffiliationRepository.findById(extensionDossier.getIdDossier())
            .orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(StatusDossier.ACCEPTE);

        assertThat(notificationsRepository.findAll())
            .as("Le BOA responsable doit recevoir l'alerte d'affectation, quel que soit le type d'affiliation")
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(responsibleBoaUser.getId())
                    && notification.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER
                    && notification.getDossierId().equals(extensionDossier.getIdDossier())
            );

        // ── Ce que consomme reellement la page "TPE a affecter" ──────────────
        // getRequests(), pas juste la notification : si le dossier n'y figure
        // pas pour le BOA responsable, la page reste vide meme si l'alerte a
        // ete recue.
        var responsibleView = staffAffiliationManagementService.getRequests(
            "Bearer " + tokenFor(responsibleBoaUser)
        );
        var visibleForResponsible = responsibleView.requests().stream()
            .filter(item -> item.dossierId().equals(extensionDossier.getIdDossier()))
            .findFirst();
        assertThat(visibleForResponsible)
            .as("Le dossier d'extension de soraya doit apparaitre dans la liste du BOA responsable")
            .isPresent();
        assertThat(visibleForResponsible.get().status()).isEqualTo("ACCEPTE");
        assertThat(visibleForResponsible.get().ecommerceSiteDejaAffecte()).isFalse();

        // Un AUTRE BOA (qui n'a jamais traite ce dossier) ne doit PAS le voir —
        // confirme que la restriction "un seul BOA responsable" fonctionne
        // comme prevu, et n'est pas la cause d'une non-visibilite inattendue
        // pour le bon compte.
        utilisateur otherBoaUser = persistUser("boa.autre@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office otherBoa = new back_office();
        otherBoa.setUtilisateur(otherBoaUser);
        backOfficeRepository.save(otherBoa);

        var otherView = staffAffiliationManagementService.getRequests("Bearer " + tokenFor(otherBoaUser));
        assertThat(otherView.requests())
            .as("Un BOA non rattache a ce dossier ne doit pas le voir dans sa propre liste")
            .noneMatch(item -> item.dossierId().equals(extensionDossier.getIdDossier()));
    }
}
