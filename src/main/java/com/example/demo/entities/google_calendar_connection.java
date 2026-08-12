package com.example.demo.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "google_calendar_connections")
@Getter
@Setter
@NoArgsConstructor
public class google_calendar_connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private utilisateur utilisateur;

    @Column(name = "refresh_token_encrypted", length = 4096)
    private String refreshTokenEncrypted;

    @Column(name = "oauth_state_hash", length = 64)
    private String oauthStateHash;

    private LocalDateTime oauthStateExpiresAt;

    private LocalDateTime connectedAt;
}
