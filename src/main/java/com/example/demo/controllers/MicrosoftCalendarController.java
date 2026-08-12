package com.example.demo.controllers;

import com.example.demo.dto.MicrosoftCalendarAuthorizationResponse;
import com.example.demo.dto.MicrosoftCalendarCallbackRequest;
import com.example.demo.dto.MicrosoftCalendarStatusResponse;
import com.example.demo.services.MicrosoftCalendarService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/microsoft-calendar")
public class MicrosoftCalendarController {

    private final MicrosoftCalendarService microsoftCalendarService;

    public MicrosoftCalendarController(MicrosoftCalendarService microsoftCalendarService) {
        this.microsoftCalendarService = microsoftCalendarService;
    }

    @GetMapping("/status")
    public ResponseEntity<MicrosoftCalendarStatusResponse> getStatus(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(microsoftCalendarService.getStatus(authorizationHeader));
    }

    @PostMapping("/authorization")
    public ResponseEntity<MicrosoftCalendarAuthorizationResponse> beginAuthorization(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(microsoftCalendarService.beginAuthorization(authorizationHeader));
    }

    @PostMapping("/callback")
    public ResponseEntity<MicrosoftCalendarStatusResponse> completeAuthorization(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody MicrosoftCalendarCallbackRequest request
    ) {
        return ResponseEntity.ok(microsoftCalendarService.completeAuthorization(authorizationHeader, request));
    }
}
