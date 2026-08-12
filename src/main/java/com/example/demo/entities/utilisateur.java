package com.example.demo.entities;

import com.example.demo.enums.RoleUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "utilisateurs")
public class utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id")
    private String keycloakId;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleUser role;

    @Column(nullable = false)
    private LocalDate dateCreation;

    @Column(nullable = false)
    private Boolean active = true;

    private LocalDate dateActivation;

    private LocalDate dateDesactivation;

    private LocalDateTime passwordExpiresAt;

    private Integer tokenVersion;

    private LocalDateTime tokenVersionUpdatedAt;

    private String loginOtpChallengeId;

    @Column(length = 512)
    private String loginOtpCodeHash;

    private LocalDateTime loginOtpExpiresAt;

    private Integer loginOtpFailedAttempts;

    @Column(length = 512)
    private String passwordResetCodeHash;

    private LocalDateTime passwordResetExpiresAt;

    private Integer passwordResetFailedAttempts;

    public utilisateur() {
    }

    @PrePersist
    public void prePersist() {
        if (this.dateCreation == null) {
            this.dateCreation = LocalDate.now();
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.tokenVersion == null) {
            this.tokenVersion = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public RoleUser getRole() {
        return role;
    }

    public void setRole(RoleUser role) {
        this.role = role;
    }

    public LocalDate getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDate dateCreation) {
        this.dateCreation = dateCreation;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDate getDateActivation() {
        return dateActivation;
    }

    public void setDateActivation(LocalDate dateActivation) {
        this.dateActivation = dateActivation;
    }

    public LocalDate getDateDesactivation() {
        return dateDesactivation;
    }

    public void setDateDesactivation(LocalDate dateDesactivation) {
        this.dateDesactivation = dateDesactivation;
    }

    public LocalDateTime getPasswordExpiresAt() {
        return passwordExpiresAt;
    }

    public void setPasswordExpiresAt(LocalDateTime passwordExpiresAt) {
        this.passwordExpiresAt = passwordExpiresAt;
    }

    public Integer getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Integer tokenVersion) {
        this.tokenVersion = tokenVersion;
        // Stocke en UTC: JwtService.isSessionInvalidated compare ce timestamp
        // au claim "iat" (epoch UTC) via toEpochSecond(ZoneOffset.UTC) — un
        // LocalDateTime.now() en heure locale y introduirait un decalage
        // (ex: UTC+1) qui invaliderait a tort les sessions fraichement emises.
        this.tokenVersionUpdatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public LocalDateTime getTokenVersionUpdatedAt() {
        return tokenVersionUpdatedAt;
    }

    public String getLoginOtpChallengeId() {
        return loginOtpChallengeId;
    }

    public void setLoginOtpChallengeId(String loginOtpChallengeId) {
        this.loginOtpChallengeId = loginOtpChallengeId;
    }

    public String getLoginOtpCodeHash() {
        return loginOtpCodeHash;
    }

    public void setLoginOtpCodeHash(String loginOtpCodeHash) {
        this.loginOtpCodeHash = loginOtpCodeHash;
    }

    public LocalDateTime getLoginOtpExpiresAt() {
        return loginOtpExpiresAt;
    }

    public void setLoginOtpExpiresAt(LocalDateTime loginOtpExpiresAt) {
        this.loginOtpExpiresAt = loginOtpExpiresAt;
    }

    public Integer getLoginOtpFailedAttempts() {
        return loginOtpFailedAttempts;
    }

    public void setLoginOtpFailedAttempts(Integer loginOtpFailedAttempts) {
        this.loginOtpFailedAttempts = loginOtpFailedAttempts;
    }

    public String getPasswordResetCodeHash() {
        return passwordResetCodeHash;
    }

    public void setPasswordResetCodeHash(String passwordResetCodeHash) {
        this.passwordResetCodeHash = passwordResetCodeHash;
    }

    public LocalDateTime getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public void setPasswordResetExpiresAt(LocalDateTime passwordResetExpiresAt) {
        this.passwordResetExpiresAt = passwordResetExpiresAt;
    }

    public Integer getPasswordResetFailedAttempts() {
        return passwordResetFailedAttempts;
    }

    public void setPasswordResetFailedAttempts(Integer passwordResetFailedAttempts) {
        this.passwordResetFailedAttempts = passwordResetFailedAttempts;
    }
}
