package com.example.demo.controllers;

import com.example.demo.services.MerchantTicketService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/commercant/transactions", "/api/merchant/transactions"})
@CrossOrigin(
    originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*",
        "https://localhost:*",
        "https://127.0.0.1:*"
    },
    allowedHeaders = {"Authorization", "Content-Type", "Accept"},
    allowCredentials = "true"
)
public class MerchantTransactionController {

    private final MerchantTicketService merchantTicketService;

    public MerchantTransactionController(MerchantTicketService merchantTicketService) {
        this.merchantTicketService = merchantTicketService;
    }

    @GetMapping("/{transactionId}/ticket")
    public ResponseEntity<ByteArrayResource> downloadTicket(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable String transactionId
    ) {
        MerchantTicketService.Ticket ticket = merchantTicketService.genererTicket(authorizationHeader, transactionId);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(ticket.nomFichier(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(ticket.contenu()));
    }
}
