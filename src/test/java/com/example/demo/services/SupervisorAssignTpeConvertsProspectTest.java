package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.ProspectStatus;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.enums.TypeNotification;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.NotificationsRepository;
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
 * Verifie que ProspectStatus.CONVERTI n'est pose qu'au moment ou le TPE est
 * reellement affecte par le BOA (assignTpeToCommercant), pas plus tot au
 * moment ou le contrat est simplement accepte/signe (finalizeAutomaticAcceptance,
 * couvert par StaffFinalizeAutomaticAcceptanceTest qui ne teste plus CONVERTI).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorAssignTpeConvertsProspectTest {

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

    @Autowired
    private NotificationsRepository notificationsRepository;

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

    private Long persistCommercialDirectMerchantDossier(String email, String tpeId) {
        utilisateur merchantUser = persistUser(email, RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Test");
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setOrigineCreation("COMMERCIAL_DIRECT");
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);

        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, null, null, "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(switchMonetiqueClient.affecter(any(), any(), any())).thenReturn(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, commercant.getIdCommercant().toString(), pointVente.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        );

        return dossier.getIdDossier();
    }

    @Test
    void assigningTpeMarksCommercialDirectDossierAsConverti() {
        utilisateur backOfficeUser = persistUser("backoffice.converti@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        String tpeId = "TPE-CONVERTI-TEST-1";
        Long dossierId = persistCommercialDirectMerchantDossier("commercant.converti@test.lanacash.ma", tpeId);

        supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            tpeId,
            new SupervisorTpeAssignRequest(dossierId)
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isEqualTo(ProspectStatus.CONVERTI);

        Long merchantUserId = reloaded.getCommercant().getUtilisateur().getId();
        assertThat(notificationsRepository.findAll())
            .anyMatch(notification ->
                notification.getUtilisateur().getId().equals(merchantUserId)
                    && notification.getTypeNotification() == TypeNotification.TPE_AFFECTE
            );
    }

    @Test
    void selfRegisteredDossierIsNotMarkedConvertiEvenAfterTpeAssignment() {
        utilisateur backOfficeUser = persistUser("backoffice.notconverti@test.lanacash.ma", RoleUser.BACK_OFFICE);
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOfficeRepository.save(backOffice);

        String tpeId = "TPE-NOTCONVERTI-TEST-1";
        utilisateur merchantUser = persistUser("commercant.autoaffiliation@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Test Auto");
        pointVente.setCommercant(commercant);
        pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setOrigineCreation("AUTO_AFFILIATION");
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        Long dossierId = dossier.getIdDossier();

        when(switchMonetiqueClient.parId(tpeId)).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, null, null, "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        ));
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of());
        when(switchMonetiqueClient.affecter(any(), any(), any())).thenReturn(
            new SwitchMonetiqueClient.SwitchTpe(
                tpeId, commercant.getIdCommercant().toString(), pointVente.getIdPDV().toString(),
                "TPE", "4G", true, BigDecimal.ZERO, LocalDateTime.now()
            )
        );

        supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            tpeId,
            new SupervisorTpeAssignRequest(dossierId)
        );

        dossier_affiliation reloaded = dossierAffiliationRepository.findById(dossierId).orElseThrow();
        assertThat(reloaded.getProspectStatus()).isNotEqualTo(ProspectStatus.CONVERTI);
    }
}
