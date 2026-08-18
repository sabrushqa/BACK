package com.example.demo.services;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Charge une seule fois (bean singleton) les logos utilisés dans les
 * documents PDF générés par l'application (tickets de transaction, fiches
 * de réclamation, contrats d'affiliation) — data: URI encodées en base64,
 * réutilisables directement dans du HTML rendu par openhtmltopdf.
 *
 * Avant : chaque service (MerchantTicketService, ReclamationPdfService,
 * GenerateurModeleContratAffiliation) lisait et ré-encodait le même fichier
 * `contrats/logo.png` indépendamment à sa propre construction — trois
 * lectures disque + trois encodages base64 du même logo au démarrage,
 * et une méthode `loadResourceAsDataUri` dupliquée trois fois. Centralisé
 * ici : un seul chargement pour toute l'application, un seul endroit à
 * corriger si le chemin/format du logo change.
 */
@Component
public class PdfLogoProvider {

    private final String lanaCash;
    private final String visa;
    private final String mastercard;
    private final String discover;
    private final String diner;
    private final String unionPay;
    private final String marocPay;

    public PdfLogoProvider() {
        this.lanaCash = loadResourceAsDataUri("contrats/logo.png");
        this.visa = loadResourceAsDataUri("contrats/logos/visa.png");
        this.mastercard = loadResourceAsDataUri("contrats/logos/mastercard.png");
        this.discover = loadResourceAsDataUri("contrats/logos/discover.png");
        this.diner = loadResourceAsDataUri("contrats/logos/diner.png");
        this.unionPay = loadResourceAsDataUri("contrats/logos/unionpay.png");
        this.marocPay = loadResourceAsDataUri("contrats/logos/marocpay.png");
    }

    /** Logo principal LanaCash — utilisé par tous les documents PDF de l'application. */
    public String lanaCash() {
        return lanaCash;
    }

    public String visa() {
        return visa;
    }

    public String mastercard() {
        return mastercard;
    }

    public String discover() {
        return discover;
    }

    public String diner() {
        return diner;
    }

    public String unionPay() {
        return unionPay;
    }

    public String marocPay() {
        return marocPay;
    }

    private String loadResourceAsDataUri(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            byte[] content = resource.getInputStream().readAllBytes();
            String mediaType = resolveMediaType(classpathLocation);
            return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(content);
        } catch (IOException exception) {
            return "";
        }
    }

    private String resolveMediaType(String classpathLocation) {
        String normalizedPath = Objects.requireNonNullElse(classpathLocation, "").toLowerCase(Locale.ROOT);

        if (normalizedPath.endsWith(".png")) {
            return "image/png";
        }
        if (normalizedPath.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (normalizedPath.endsWith(".jpg") || normalizedPath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (normalizedPath.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }
}
