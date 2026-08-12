package com.example.demo.controllers;

import com.example.demo.dto.AffiliationActionResponse;
import com.example.demo.dto.AffiliationActivationRequest;
import com.example.demo.dto.AffiliationAbandonRequest;
import com.example.demo.dto.AffiliationReviewRequest;
import com.example.demo.dto.CommercialAffiliationDraftRequest;
import com.example.demo.dto.CommercialInteractionRequest;
import com.example.demo.dto.CommercialInteractionResponse;
import com.example.demo.dto.StaffAffiliationOverviewResponse;
import com.example.demo.services.StaffAffiliationManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/staff/affiliations")
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
public class StaffAffiliationController {

    private final StaffAffiliationManagementService staffAffiliationManagementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StaffAffiliationController(
        StaffAffiliationManagementService staffAffiliationManagementService
    ) {
        this.staffAffiliationManagementService = staffAffiliationManagementService;
    }

    @GetMapping
    public ResponseEntity<StaffAffiliationOverviewResponse> getRequests(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.getRequests(authorizationHeader)
        );
    }

    @PostMapping("/{dossierId}/complète")
    public ResponseEntity<AffiliationActionResponse> completeMerchantDossier(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        @RequestBody AffiliationActivationRequest request
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.completeMerchantDossier(
                authorizationHeader,
                dossierId,
                request
            )
        );
    }

    @PostMapping(value = "/drafts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AffiliationActionResponse> createCommercialDraft(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody CommercialAffiliationDraftRequest request
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.createCommercialDraft(
                authorizationHeader,
                request
            )
        );
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliationActionResponse> createCommercialDraftMultipart(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        MultipartHttpServletRequest multipartRequest
    ) {
        Map<String, MultipartFile> uploadedDocuments = new LinkedHashMap<>(multipartRequest.getFileMap());
        CommercialAffiliationDraftRequest request = resolveCommercialDraftPayload(multipartRequest);
        return ResponseEntity.ok(
            staffAffiliationManagementService.createCommercialDraft(
                authorizationHeader,
                request,
                uploadedDocuments
            )
        );
    }

    @PostMapping(value = "/{dossierId}/draft", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AffiliationActionResponse> saveCommercialDraft(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        @RequestBody CommercialAffiliationDraftRequest request
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.saveCommercialDraft(
                authorizationHeader,
                dossierId,
                request
            )
        );
    }

    @PostMapping(value = "/{dossierId}/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AffiliationActionResponse> saveCommercialDraftMultipart(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        MultipartHttpServletRequest multipartRequest
    ) {
        Map<String, MultipartFile> uploadedDocuments = new LinkedHashMap<>(multipartRequest.getFileMap());
        CommercialAffiliationDraftRequest request = resolveCommercialDraftPayload(multipartRequest);
        return ResponseEntity.ok(
            staffAffiliationManagementService.saveCommercialDraft(
                authorizationHeader,
                dossierId,
                request,
                uploadedDocuments
            )
        );
    }

    @GetMapping("/{dossierId}/interactions")
    public ResponseEntity<CommercialInteractionResponse> getCommercialInteractions(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.getCommercialInteractions(
                authorizationHeader,
                dossierId
            )
        );
    }

    @PostMapping("/{dossierId}/interactions")
    public ResponseEntity<CommercialInteractionResponse> addCommercialInteraction(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        @RequestBody CommercialInteractionRequest request
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.addCommercialInteraction(
                authorizationHeader,
                dossierId,
                request
            )
        );
    }

    @PostMapping("/{dossierId}/review")
    public ResponseEntity<AffiliationActionResponse> reviewMerchantDossier(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        @RequestBody AffiliationReviewRequest request
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.reviewMerchantDossier(
                authorizationHeader,
                dossierId,
                request
            )
        );
    }

    @PostMapping("/{dossierId}/abandon")
    public ResponseEntity<AffiliationActionResponse> abandonCorrectionDossier(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        @RequestBody AffiliationAbandonRequest request
    ) {
        return ResponseEntity.ok(
            staffAffiliationManagementService.abandonCorrectionDossier(
                authorizationHeader,
                dossierId,
                request
            )
        );
    }

    private CommercialAffiliationDraftRequest resolveCommercialDraftPayload(
        MultipartHttpServletRequest multipartRequest
    ) {
        String payload = multipartRequest.getParameter("payload");
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("Le contenu du brouillon commercial est obligatoire.");
        }

        try {
            return objectMapper.readValue(payload, CommercialAffiliationDraftRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Le contenu du brouillon commercial est invalide.");
        }
    }

    @GetMapping("/{dossierId}/documents/{documentId}/download")
    public ResponseEntity<ByteArrayResource> downloadDocument(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId,
        @PathVariable Long documentId
    ) {
        StaffAffiliationManagementService.DocumentDownload documentDownload =
            staffAffiliationManagementService.downloadDocument(
                authorizationHeader,
                dossierId,
                documentId
            );

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(documentDownload.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(documentDownload.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(documentDownload.content()));
    }

    @GetMapping("/{dossierId}/contract/download")
    public ResponseEntity<ByteArrayResource> downloadGeneratedContract(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId
    ) {
        StaffAffiliationManagementService.DocumentDownload documentDownload =
            staffAffiliationManagementService.downloadContratGenere(
                authorizationHeader,
                dossierId
            );

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(documentDownload.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(documentDownload.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(documentDownload.content()));
    }

    @GetMapping("/{dossierId}/contract/signed/download")
    public ResponseEntity<ByteArrayResource> downloadSignedContract(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId
    ) {
        StaffAffiliationManagementService.DocumentDownload documentDownload =
            staffAffiliationManagementService.downloadSignedContract(
                authorizationHeader,
                dossierId
            );

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(documentDownload.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(documentDownload.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(documentDownload.content()));
    }

    @GetMapping("/{dossierId}/full-dossier/download")
    public ResponseEntity<ByteArrayResource> downloadFullDossier(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId
    ) {
        StaffAffiliationManagementService.DocumentDownload documentDownload =
            staffAffiliationManagementService.downloadFullDossier(
                authorizationHeader,
                dossierId
            );

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(documentDownload.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(documentDownload.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(documentDownload.content()));
    }

    @GetMapping("/{dossierId}/commercial-report/download")
    public ResponseEntity<ByteArrayResource> downloadCommercialReport(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @PathVariable Long dossierId
    ) {
        StaffAffiliationManagementService.DocumentDownload documentDownload =
            staffAffiliationManagementService.downloadCommercialReport(
                authorizationHeader,
                dossierId
            );

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(documentDownload.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(documentDownload.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(new ByteArrayResource(documentDownload.content()));
    }
}
