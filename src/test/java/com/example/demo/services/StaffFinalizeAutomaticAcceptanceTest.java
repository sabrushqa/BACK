package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.entities.back_office;
import com.example.demo.entities.commerciale;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.CommercialeRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exerce l'acceptation automatique d'un dossier (declenchee quand le
 * commerçant depose un contrat signe valide), pour un dossier standard
 * (activation de tous les PDV du commerçant) et pour une demande de
 * nouveau point de vente (activation du seul PDV demande). Dans les deux
 * cas, une reference TPE/SoftPOS/QR doit etre affectee MANUELLEMENT par le
 * BOA (mail + notification DOSSIER_TPE_A_AFFECTER) — aucun terminal n'est
 * plus auto-provisionne, y compris pour une demande d'extension (voir
 * finalizeAutomaticAcceptance : l'ancien provisionRequestedTerminals
 * contournait l'affectation reelle et ne prevenait jamais le BOA).
 */
@SpringBootTest
@Transactional
class StaffFinalizeAutomaticAcceptanceTest {

    @Autowired
    private StaffAffiliationManagementService staffAffiliationManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private CommercialeRepository commercialeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    private utilisateur persistUser(String email, RoleUser role) {
        utilisateur user = new utilisateur();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setDateCreation(LocalDate.now());
        return utilisateurRepository.save(user);
    }

    @Test
    void acceptsRegularDossierAndActivatesAllMerchantPdvs() {
        utilisateur merchantUser = persistUser("merchant.finalize.regular@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Finalize Regular");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente.setStatut("EN_ATTENTE");
        pointVente = pdvRepository.save(pointVente);

        utilisateur commercialUser = persistUser("commercial.finalize.regular@test.lanacash.ma", RoleUser.COMMERCIAL);
        commerciale commerciale = new commerciale();
        commerciale.setUtilisateur(commercialUser);
        commerciale = commercialeRepository.save(commerciale);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setCommerciale(commerciale);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.ACCEPTE);

        pdv reloadedPdv = pdvRepository.findById(pointVente.getIdPDV()).orElseThrow();
        assertThat(reloadedPdv.getStatut()).isEqualTo("ACTIF");

        assertThat(notificationsRepository.findAll())
            .anyMatch(notification -> notification.getUtilisateur().getId().equals(merchantUser.getId()));
        assertThat(notificationsRepository.findAll())
            .anyMatch(notification -> notification.getUtilisateur().getId().equals(commercialUser.getId()));
    }

    @Test
    void acceptsNewPdvDossierAndActivatesOnlyTheRequestedPdv() {
        utilisateur merchantUser = persistUser("merchant.finalize.newpdv@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Finalize NewPdv");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv requestedPdv = new pdv();
        requestedPdv.setCommercant(commercant);
        requestedPdv.setStatut("EN_ATTENTE");
        requestedPdv = pdvRepository.save(requestedPdv);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setNombreTpe(2);
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setRequestedPdv(requestedPdv);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();
        final Long pdvId = requestedPdv.getIdPDV();

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(StatusDossier.ACCEPTE);

        pdv reloadedPdv = pdvRepository.findById(pdvId).orElseThrow();
        assertThat(reloadedPdv.getStatut()).isEqualTo("ACTIF");

        // Plus d'auto-provisionnement : aucun TPE ne doit exister tant que le
        // BOA ne l'a pas reellement affecte (assignTpeToCommercant).
        assertThat(tpeRepository.countByPdv_IdPDV(pdvId)).isZero();
    }

    @Test
    void notifiesBackOfficeForTpeAssignmentOnNewPdvDossierInsteadOfAutoProvisioning() {
        utilisateur boaUser = persistUser("boa.notify.newpdv@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        backOfficeRepository.save(boa);

        utilisateur merchantUser = persistUser("merchant.finalize.softpos@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Finalize Softpos");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv requestedPdv = new pdv();
        requestedPdv.setCommercant(commercant);
        requestedPdv.setStatut("EN_ATTENTE");
        requestedPdv = pdvRepository.save(requestedPdv);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.SOFTPOS);
        dossier.setNombreTpe(1);
        dossier.setModeleQrSoftpos("SOFTPOS PRO");
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setRequestedPdv(requestedPdv);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();
        final Long pdvId = requestedPdv.getIdPDV();

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        assertThat(tpeRepository.countByPdv_IdPDV(pdvId)).isZero();
        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(boaUser.getId())
                    && notification.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER
                    && notification.getDossierId().equals(dossierId)
            );
    }

    @Test
    void notifiesBackOfficeForEcommerceOnlyNewPdvDossier() {
        // Depuis assignEcommerceSiteToCommercant, E_COMMERCE a lui aussi besoin
        // d'une affectation manuelle (site marchand a interfacer avec Switch) —
        // ce dossier ne doit donc plus etre exclu de l'alerte BOA.
        utilisateur boaUser = persistUser("boa.notify.newpdv.ecom@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        backOfficeRepository.save(boa);

        utilisateur merchantUser = persistUser("merchant.finalize.newpdv.ecom@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Finalize NewPdv Ecom");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        dossier.setOrigineCreation("NOUVEAU_PDV");
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(boaUser.getId())
                    && notification.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER
                    && notification.getMessage().contains("site e-commerce")
            );
    }

    @Test
    void notifiesAllBackOfficeUsersWhenTpeAssignmentNeeded() {
        utilisateur boaUser = persistUser("boa.notify.tpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        backOfficeRepository.save(boa);

        utilisateur merchantUser = persistUser("merchant.notify.tpe@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Notify Tpe");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(boaUser.getId())
                    && notification.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER
            );
    }

    @Test
    void notifiesBackOfficeForEcommerceOnlyDossier() {
        utilisateur boaUser = persistUser("boa.notify.ecommerce@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        backOfficeRepository.save(boa);

        utilisateur merchantUser = persistUser("merchant.notify.ecommerce@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Notify Ecommerce");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(boaUser.getId())
                    && notification.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER
                    && notification.getMessage().contains("site e-commerce")
            );
    }

    @Test
    void notifiesBackOfficeForBothTpeAndEcommerceOnCombinedDossier() {
        utilisateur boaUser = persistUser("boa.notify.combined@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office boa = new back_office();
        boa.setUtilisateur(boaUser);
        backOfficeRepository.save(boa);

        utilisateur merchantUser = persistUser("merchant.notify.combined@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Notify Combined");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(boaUser.getId())
                    && notification.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER
                    && notification.getMessage().contains("TPE/SoftPOS/QR")
                    && notification.getMessage().contains("site e-commerce")
            );
    }

    @Test
    void notifiesOnlyTheAssignedBackOfficeWhenTheDossierAlreadyHasOne() {
        utilisateur assignedBoaUser = persistUser("boa.assigned.notify.tpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office assignedBoa = new back_office();
        assignedBoa.setUtilisateur(assignedBoaUser);
        assignedBoa = backOfficeRepository.save(assignedBoa);

        utilisateur otherBoaUser = persistUser("boa.other.notify.tpe@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office otherBoa = new back_office();
        otherBoa.setUtilisateur(otherBoaUser);
        backOfficeRepository.save(otherBoa);

        utilisateur merchantUser = persistUser("merchant.notify.assignedboa@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Notify Assigned Boa");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setBackOffice(assignedBoa);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        staffAffiliationManagementService.finalizeAutomaticAcceptance(dossier);

        assertThat(notificationsRepository.findAll())
            .anyMatch(n -> n.getUtilisateur().getId().equals(assignedBoaUser.getId())
                && n.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER);
        assertThat(notificationsRepository.findAll())
            .noneMatch(n -> n.getUtilisateur().getId().equals(otherBoaUser.getId())
                && n.getTypeNotification() == TypeNotification.DOSSIER_TPE_A_AFFECTER);
    }

    @Test
    void rejectsFinalizationWhenMerchantAccountIsDeactivated() {
        utilisateur merchantUser = persistUser("merchant.finalize.deactivated@test.lanacash.ma", RoleUser.COMMERCANT);
        merchantUser.setDateDesactivation(LocalDate.now());
        merchantUser = utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Finalize Deactivated");
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.CONTRAT_A_SIGNER);
        dossier.setDateSoumission(LocalDate.now());
        final dossier_affiliation savedDossier = dossierAffiliationRepository.save(dossier);

        assertThatThrownBy(() -> staffAffiliationManagementService.finalizeAutomaticAcceptance(savedDossier))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
