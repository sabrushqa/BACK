package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.demo.dto.SupervisorCommercantTransactionsResponse;
import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifie la page superviseur "Transactions" (liste deroulante de
 * commercants) : recuperation de l'historique d'un commercant choisi, tri
 * par date decroissante, resolution du PDV via le stock Oracle, et le
 * telechargement de ticket depuis cet espace (controle par role SUPERVISEUR,
 * pas par propriete du token comme cote commercant).
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class SupervisorCommercantTransactionsTest {

    @Autowired
    private SupervisorManagementService supervisorManagementService;

    @Autowired
    private MerchantTicketService merchantTicketService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private CommercantRepository commercantRepository;

    @Autowired
    private PdvRepository pdvRepository;

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
    void supervisorSeesCommercantTransactionsSortedByMostRecentFirst() {
        utilisateur superviseur = persistUser("superviseur.transactions@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur commercantUser = persistUser("commercant.pourtransactions@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant.setNomCommercial("Beny Youness");
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV1_AEENO");
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        when(switchMonetiqueClient.stockComplet()).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-000003", commercantId.toString(), pointVente.getIdPDV().toString(),
                "TPE", "ETHERNET", true, BigDecimal.valueOf(50000), LocalDateTime.now()
            )
        ));
        when(switchMonetiqueClient.transactions(commercantId.toString())).thenReturn(List.of(
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-3-000001", "TPE", "TPE-000003", null, commercantId.toString(),
                BigDecimal.valueOf(610.66), "MAD", "ACHAT", "APPROVED", "NFC",
                LocalDateTime.parse("2026-01-01T09:43:47"),
                null, null, null, null, null, null
            ),
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-3-000504", "TPE", "TPE-000003", null, commercantId.toString(),
                BigDecimal.valueOf(493.48), "MAD", "ACHAT", "APPROVED", "BANDE",
                LocalDateTime.parse("2026-04-30T19:14:39"),
                null, null, null, null, null, null
            )
        ));

        SupervisorCommercantTransactionsResponse response = supervisorManagementService.getCommercantTransactions(
            "Bearer " + tokenFor(superviseur),
            commercantId
        );

        assertThat(response.commercantNom()).isEqualTo("Beny Youness");
        assertThat(response.transactions()).hasSize(2);
        assertThat(response.transactions().get(0).id()).isEqualTo("TX-3-000504");
        assertThat(response.transactions().get(0).pdv()).isEqualTo("PDV1_AEENO");
        assertThat(response.transactions().get(1).id()).isEqualTo("TX-3-000001");
    }

    @Test
    void nonSupervisorCannotListCommercantTransactions() {
        utilisateur backOffice = persistUser("backoffice.transactions@test.lanacash.ma", RoleUser.BACK_OFFICE);
        utilisateur commercantUser = persistUser("commercant.refus@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant = commercantRepository.save(commercant);
        final Long commercantId = commercant.getIdCommercant();

        assertThatThrownBy(() ->
            supervisorManagementService.getCommercantTransactions("Bearer " + tokenFor(backOffice), commercantId)
        ).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void supervisorCanDownloadTicketForAnyCommercant() {
        utilisateur superviseur = persistUser("superviseur.ticket@test.lanacash.ma", RoleUser.SUPERVISEUR);
        utilisateur commercantUser = persistUser("commercant.pourticket@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant.setNomCommercial("Beny Youness");
        commercant = commercantRepository.save(commercant);
        Long commercantId = commercant.getIdCommercant();

        when(switchMonetiqueClient.transaction("TX-3-000504")).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-3-000504", "TPE", "TPE-000003", null, commercantId.toString(),
                BigDecimal.valueOf(493.48), "MAD", "ACHAT", "APPROVED", "BANDE",
                LocalDateTime.parse("2026-04-30T19:14:39"),
                null, null, null, null, null, null
            )
        ));
        when(switchMonetiqueClient.parId("TPE-000003")).thenReturn(Optional.empty());

        MerchantTicketService.Ticket ticket = merchantTicketService.genererTicketPourSupervision(
            "Bearer " + tokenFor(superviseur),
            commercantId,
            "TX-3-000504"
        );

        assertThat(ticket.contenu()).isNotEmpty();
        assertThat(new String(ticket.contenu(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void nonSupervisorCannotDownloadTicketFromSupervisorSpace() {
        utilisateur commercial = persistUser("commercial.ticket@test.lanacash.ma", RoleUser.COMMERCIAL);
        utilisateur commercantUser = persistUser("commercant.ticketrefus@test.lanacash.ma", RoleUser.COMMERCANT);
        commercant commercant = new commercant();
        commercant.setUtilisateur(commercantUser);
        commercant = commercantRepository.save(commercant);
        final Long commercantId = commercant.getIdCommercant();

        assertThatThrownBy(() ->
            merchantTicketService.genererTicketPourSupervision(
                "Bearer " + tokenFor(commercial),
                commercantId,
                "TX-3-000504"
            )
        ).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
}
