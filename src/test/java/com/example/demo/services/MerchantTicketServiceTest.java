package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifie la generation du ticket PDF telechargeable pour une transaction
 * (bouton "Ticket" de CommercantTransactionsPage) et son controle d'acces :
 * un commercant ne doit pouvoir telecharger que ses propres transactions.
 */
@SpringBootTest
@Import(TestJwtSupport.class)
@Transactional
class MerchantTicketServiceTest {

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

    private String tokenFor(utilisateur user) {
        return TestJwtSupport.mintKeycloakToken("kc-sub-" + user.getId(), user.getEmail(), 300);
    }

    private commercant createMerchant(String email) {
        utilisateur merchantUser = new utilisateur();
        merchantUser.setEmail(email);
        merchantUser.setRole(RoleUser.COMMERCANT);
        merchantUser.setActive(true);
        merchantUser.setDateCreation(LocalDate.now());
        utilisateurRepository.save(merchantUser);

        commercant commercant = new commercant();
        commercant.setUtilisateur(merchantUser);
        commercant.setNomCommercial("Beny Youness");
        return commercantRepository.save(commercant);
    }

    @Test
    void generatesPdfTicketForOwnTransaction() throws java.io.IOException {
        commercant commercant = createMerchant("ticket.owner@test.lanacash.ma");
        Long commercantId = commercant.getIdCommercant();

        pdv pointVente = new pdv();
        pointVente.setNomPDV("PDV Beny Youness");
        pointVente.setCommercant(commercant);
        pointVente = pdvRepository.save(pointVente);

        when(switchMonetiqueClient.transaction("TX-3-000504")).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-3-000504", "TPE", "TPE-000003", null, commercantId.toString(),
                BigDecimal.valueOf(493.48), "MAD", "ACHAT", "APPROVED", "BANDE",
                LocalDateTime.parse("2026-04-30T19:14:39"),
                "000121", "215530", "007075",
                "XXXXXXXXXXXX0421", "MASTERCARD", "A0000000041010"
            )
        ));
        when(switchMonetiqueClient.parId("TPE-000003")).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTpe(
                "TPE-000003", commercantId.toString(), pointVente.getIdPDV().toString(),
                "TPE", "ETHERNET", true, BigDecimal.valueOf(50000), LocalDateTime.now()
            )
        ));

        MerchantTicketService.Ticket ticket = merchantTicketService.genererTicket(
            "Bearer " + tokenFor(commercant.getUtilisateur()),
            "TX-3-000504"
        );

        assertThat(ticket.nomFichier()).isEqualTo("ticket-TX-3-000504.pdf");
        assertThat(ticket.contenu()).isNotEmpty();
        // Signature PDF standard : le fichier genere doit etre un vrai PDF exploitable.
        assertThat(new String(ticket.contenu(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Format "vrai ticket TPE" (voir MerchantTicketService::buildTicketHtml) :
        // AID EMV, carte masquee + schema, STAN/autorisation doivent apparaitre
        // tels quels dans le texte du PDF genere — pas juste "un PDF valide".
        String text;
        try (PDDocument pdf = PDDocument.load(ticket.contenu())) {
            text = new PDFTextStripper().getText(pdf);
        }
        assertThat(text).contains("TICKET COMMERÇANT À CONSERVER");
        assertThat(text).contains("A0000000041010"); // AID Mastercard
        assertThat(text).contains("XXXXXXXXXXXX0421");
        assertThat(text).contains("MASTERCARD");
        assertThat(text).contains("TX-3-000504"); // N° TRANSACTION
        assertThat(text).contains("007075"); // N° AUTORISATION (codeAutorisation)
        assertThat(text).contains("000121"); // STAN
        assertThat(text).contains("215530"); // RRN
        assertThat(text).contains("TICKET CLIENT");
    }

    @Test
    void rejectsTicketForAnotherMerchantsTransaction() {
        commercant owner = createMerchant("ticket.owner2@test.lanacash.ma");
        commercant intruder = createMerchant("ticket.intruder@test.lanacash.ma");

        when(switchMonetiqueClient.transaction("TX-OTHER-1")).thenReturn(Optional.of(
            new SwitchMonetiqueClient.SwitchTransaction(
                "TX-OTHER-1", "TPE", "TPE-999", null, owner.getIdCommercant().toString(),
                BigDecimal.TEN, "MAD", "ACHAT", "APPROVED", "PUCE", LocalDateTime.now(),
                null, null, null, null, null, null
            )
        ));

        assertThatThrownBy(() -> merchantTicketService.genererTicket(
            "Bearer " + tokenFor(intruder.getUtilisateur()),
            "TX-OTHER-1"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403");
    }

    @Test
    void returns404WhenTransactionDoesNotExist() {
        commercant commercant = createMerchant("ticket.notfound@test.lanacash.ma");
        when(switchMonetiqueClient.transaction("TX-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantTicketService.genererTicket(
            "Bearer " + tokenFor(commercant.getUtilisateur()),
            "TX-MISSING"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }
}
