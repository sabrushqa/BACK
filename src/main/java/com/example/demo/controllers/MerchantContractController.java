package com.example.demo.controllers;

import com.example.demo.dto.AffiliationActionResponse;
import com.example.demo.dto.ContractSignatureVerificationResponse;
import com.example.demo.dto.MerchantContractOverviewResponse;
import com.example.demo.services.MerchantContractManagementService;
import com.example.demo.services.ServiceDocumentContratAffiliation;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/commercant/contracts", "/api/merchant/contracts"})
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
public class MerchantContractController {

    private final MerchantContractManagementService merchantContractManagementService;

    public MerchantContractController(
        MerchantContractManagementService merchantContractManagementService
    ) {
        this.merchantContractManagementService = merchantContractManagementService;
    }

    @GetMapping("/latest")
    public ResponseEntity<MerchantContractOverviewResponse> getLatestContract(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(
            merchantContractManagementService.getLatestContract(authorizationHeader)
        );
    }

    @GetMapping("/latest/download")
    public ResponseEntity<ByteArrayResource> downloadLatestContract(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        ServiceDocumentContratAffiliation.ContratTelecharge contractDownload =
            merchantContractManagementService.downloadLatestContratGenere(authorizationHeader);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contractDownload.typeContenu()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(contractDownload.nomFichier(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(contractDownload.contenu()));
    }

    @PostMapping(value = "/verify-signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContractSignatureVerificationResponse> verifySignature(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
            merchantContractManagementService.verifySignature(authorizationHeader, file)
        );
    }

    @PostMapping(value = "/latest/upload-signed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliationActionResponse> uploadSignedContract(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
            merchantContractManagementService.uploadSignedContract(authorizationHeader, file)
        );
    }
}
