package com.example.demo.controllers;

import com.example.demo.dto.GoogleCalendarAuthorizationResponse;
import com.example.demo.dto.GoogleCalendarCallbackRequest;
import com.example.demo.dto.GoogleCalendarStatusResponse;
import com.example.demo.services.GoogleCalendarService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff/google-calendar")
public class GoogleCalendarController {

    private final GoogleCalendarService googleCalendarService;

    public GoogleCalendarController(GoogleCalendarService googleCalendarService) {
        this.googleCalendarService = googleCalendarService;
    }

    @GetMapping("/status")
    public ResponseEntity<GoogleCalendarStatusResponse> getStatus(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(googleCalendarService.getStatus(authorizationHeader));
    }

    @PostMapping("/authorization")
    public ResponseEntity<GoogleCalendarAuthorizationResponse> beginAuthorization(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(googleCalendarService.beginAuthorization(authorizationHeader));
    }

    @PostMapping("/callback")
    public ResponseEntity<GoogleCalendarStatusResponse> completeAuthorization(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
        @RequestBody GoogleCalendarCallbackRequest request
    ) {
        return ResponseEntity.ok(googleCalendarService.completeAuthorization(authorizationHeader, request));
    }
}
