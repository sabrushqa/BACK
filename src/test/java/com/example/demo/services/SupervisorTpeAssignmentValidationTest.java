package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.SupervisorTpeAssignRequest;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.pdv;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.enums.StatusDossier;
import com.example.demo.enums.TypeAffiliation;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.TpeRepository;
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
 * Exerce les branches de rejet de validateTpeAssignment (dossier non valide,
 * e-commerce, type de reference incompatible, quota deja atteint) et les
 * heuristiques de resolveEffectiveTpeType pour ENCAISSEMENT_ET_ECOMMERCE, qui
 * n'etaient exercees que par le chemin heureux (assignsTpeToCommercantWithValidatedDossier).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorTpeAssignmentValidationTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private BackOfficeRepository backOfficeRepository;

    @Autowired
    private DossierAffiliationRepository dossierAffiliationRepository;

    @Autowired
    private PdvRepository pdvRepository;

    @Autowired
    private TpeRepository tpeRepository;

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

    private utilisateur persistBackOfficeWithPermission() {
        utilisateur backOfficeUser = persistUser(
            "backoffice.tpevalidation." + System.nanoTime() + "@test.lanacash.ma", RoleUser.BACK_OFFICE
        );
        back_office backOffice = new back_office();
        backOffice.setUtilisateur(backOfficeUser);
        backOffice.setPeutAffecterTpe(true);
        backOfficeRepository.save(backOffice);
        return backOfficeUser;
    }

    private tpe persistActiveTerminal(String serial, String typeCompatible) {
        tpe terminal = new tpe();
        terminal.setNumeroSerie(serial);
        terminal.setActif(true);
        terminal.setTypeCompatible(typeCompatible);
        terminal = tpeRepository.save(terminal);
        String switchId = terminal.getIdTPE().toString();
        when(switchMonetiqueClient.parId(switchId)).thenReturn(Optional.of(
            switchTpe(switchId, typeCompatible, true, null)
        ));
        return terminal;
    }

    private SwitchMonetiqueClient.SwitchTpe switchTpe(
        String id,
        String nature,
        boolean active,
        String commercantId
    ) {
        return new SwitchMonetiqueClient.SwitchTpe(
            id, commercantId, null, nature, "4G", active, BigDecimal.ZERO, LocalDateTime.now()
        );
    }

    @Test
    void rejectsAssignmentWhenDossierNotYetValidated() {
        utilisateur backOfficeUser = persistBackOfficeWithPermission();

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Tpe Not Validated");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.EN_ATTENTE_VALIDATION_BOA);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        tpe terminal = persistActiveTerminal("TPE-NOTVALIDATED-1", "TPE");

        assertThatThrownBy(() -> supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            terminal.getIdTPE().toString(),
            new SupervisorTpeAssignRequest(dossierId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Le contrat doit être signé et déposé");
    }

    @Test
    void rejectsAssignmentForEcommerceDossier() {
        utilisateur backOfficeUser = persistBackOfficeWithPermission();

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Tpe Ecommerce");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.E_COMMERCE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        tpe terminal = persistActiveTerminal("TPE-ECOMMERCE-1", "TPE");

        assertThatThrownBy(() -> supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            terminal.getIdTPE().toString(),
            new SupervisorTpeAssignRequest(dossierId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("e-commerce");
    }

    @Test
    void rejectsAssignmentWhenTerminalTypeDoesNotMatchDossierType() {
        utilisateur backOfficeUser = persistBackOfficeWithPermission();

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Tpe Wrong Type");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.SOFTPOS);
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        tpe terminal = persistActiveTerminal("TPE-WRONGTYPE-1", "TPE");

        assertThatThrownBy(() -> supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            terminal.getIdTPE().toString(),
            new SupervisorTpeAssignRequest(dossierId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Référence incompatible");
    }

    @Test
    void rejectsAssignmentWhenTpeQuotaAlreadyReached() {
        utilisateur backOfficeUser = persistBackOfficeWithPermission();

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Tpe Quota Reached");
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        tpe alreadyAssigned = new tpe();
        alreadyAssigned.setNumeroSerie("TPE-QUOTA-ALREADY-1");
        alreadyAssigned.setActif(true);
        alreadyAssigned.setTypeCompatible("TPE");
        alreadyAssigned.setStatut("AFFECTE_COMMERCANT");
        alreadyAssigned.setPdv(pointVente);
        tpeRepository.save(alreadyAssigned);

        tpe terminal = persistActiveTerminal("TPE-QUOTA-NEW-1", "TPE");
        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            switchTpe(
                "TPE-QUOTA-ALREADY-1",
                "TPE",
                true,
                commercant.getIdCommercant().toString()
            )
        ));

        assertThatThrownBy(() -> supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            terminal.getIdTPE().toString(),
            new SupervisorTpeAssignRequest(dossierId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("atteint déjà le nombre demandé");
    }

    @Test
    void rejectsTpeDossierWithoutExplicitCount() {
        utilisateur backOfficeUser = persistBackOfficeWithPermission();

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Tpe No Count");
        commercant = commercantRepository.save(commercant);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.TPE);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        tpe terminal = persistActiveTerminal("TPE-NOCOUNT-1", "TPE");

        assertThatThrownBy(() -> supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            terminal.getIdTPE().toString(),
            new SupervisorTpeAssignRequest(dossierId)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nombre de TPE demandé");
    }

    @Test
    void assignsQrCodeReferenceToCombinedDossierUsingModeleQrSoftposHeuristic() {
        utilisateur backOfficeUser = persistBackOfficeWithPermission();

        commercant commercant = new commercant();
        commercant.setNomCommercial("Boutique Combined Qr");
        commercant = commercantRepository.save(commercant);

        pdv pointVente = new pdv();
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        dossier_affiliation dossier = new dossier_affiliation();
        dossier.setCommercant(commercant);
        dossier.setStatus(StatusDossier.ACCEPTE);
        dossier.setTypeAffiliation(TypeAffiliation.ENCAISSEMENT_ET_ECOMMERCE);
        dossier.setModeleQrSoftpos("QR_STATIQUE");
        dossier.setNombreTpe(1);
        dossier.setDateSoumission(LocalDate.now());
        dossier = dossierAffiliationRepository.save(dossier);
        final Long dossierId = dossier.getIdDossier();

        tpe terminal = persistActiveTerminal("TPE-COMBINED-QR-1", "QR_CODE");
        String switchId = terminal.getIdTPE().toString();

        supervisorManagementService.assignTpeToCommercant(
            "Bearer " + tokenFor(backOfficeUser),
            switchId,
            new SupervisorTpeAssignRequest(dossierId)
        );

        verify(switchMonetiqueClient).affecter(
            eq(switchId),
            eq(commercant.getIdCommercant().toString()),
            eq(pointVente.getIdPDV().toString()),
            any(), any(), any()
        );
    }
}
