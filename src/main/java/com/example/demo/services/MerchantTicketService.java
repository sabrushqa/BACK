package com.example.demo.services;

import com.example.demo.entities.commercant;
import com.example.demo.entities.pdv;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.PdvRepository;
import com.example.demo.repositories.UtilisateurRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Genere le ticket PDF (format reçu de caisse) d'une transaction individuelle,
 * a la demande du commerçant depuis son historique — les transactions elles-
 * memes vivent cote switch-monetique-service (Oracle), voir
 * SwitchMonetiqueClient::transaction.
 */
@Service
public class MerchantTicketService {

    private static final DateTimeFormatter TICKET_DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TICKET_TIME_ONLY_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MerchantAccessService merchantAccessService;
    private final SwitchMonetiqueClient switchMonetiqueClient;
    private final CommercantRepository commercantRepository;
    private final PdvRepository pdvRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JwtService jwtService;
    private final PdfLogoProvider pdfLogoProvider;

    public MerchantTicketService(
        MerchantAccessService merchantAccessService,
        SwitchMonetiqueClient switchMonetiqueClient,
        CommercantRepository commercantRepository,
        PdvRepository pdvRepository,
        UtilisateurRepository utilisateurRepository,
        JwtService jwtService,
        PdfLogoProvider pdfLogoProvider
    ) {
        this.merchantAccessService = merchantAccessService;
        this.switchMonetiqueClient = switchMonetiqueClient;
        this.commercantRepository = commercantRepository;
        this.pdvRepository = pdvRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.jwtService = jwtService;
        this.pdfLogoProvider = pdfLogoProvider;
    }

    public record Ticket(byte[] contenu, String nomFichier) {
        // Voir MerchantContractManagementService.ContratTelecharge : equals/hashCode
        // par defaut sur un champ tableau compare des references, pas le contenu
        // (Sonar S6218).
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Ticket that)) {
                return false;
            }
            return Arrays.equals(contenu, that.contenu) && Objects.equals(nomFichier, that.nomFichier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(contenu), nomFichier);
        }

        @Override
        public String toString() {
            return "Ticket[contenu=" + (contenu == null ? "null" : contenu.length + " octets")
                + ", nomFichier=" + nomFichier + "]";
        }
    }

    public Ticket genererTicket(String authorizationHeader, String idTransaction) {
        Long commercantId = merchantAccessService.resolveAuthenticatedCommercantId(authorizationHeader);
        if (commercantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session commerçant invalide.");
        }
        return buildTicket(commercantId, idTransaction);
    }

    /**
     * Meme ticket, mais depuis l'espace superviseur : le commerçant est choisi
     * dans la liste déroulante (pas déduit du token), on verifie donc un rôle
     * SUPERVISEUR plutôt qu'une propriété de la transaction par le token.
     */
    public Ticket genererTicketPourSupervision(String authorizationHeader, Long commercantId, String idTransaction) {
        readAuthenticatedSupervisor(authorizationHeader);
        return buildTicket(commercantId, idTransaction);
    }

    private Ticket buildTicket(Long commercantId, String idTransaction) {
        SwitchMonetiqueClient.SwitchTransaction transaction = switchMonetiqueClient.transaction(idTransaction)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction introuvable."));

        if (!commercantId.toString().equals(transaction.idCommercant())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Cette transaction n'appartient pas à ce commerçant."
            );
        }

        commercant commercant = commercantRepository.findById(commercantId).orElse(null);
        String merchantName = commercant == null
            ? ""
            : firstNotBlank(commercant.getNomCommercial(), commercant.getRaisonSociale());
        String merchantVille = commercant == null ? "" : safe(commercant.getVille());

        String pdvName = resolvePdvName(transaction);
        String html = buildTicketHtml(transaction, merchantName, pdvName, merchantVille);
        byte[] pdf = renderPdf(html);
        return new Ticket(pdf, "ticket-" + idTransaction + ".pdf");
    }

    private void readAuthenticatedSupervisor(String authorizationHeader) {
        String token = jwtService.extractBearerToken(authorizationHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification Keycloak requise."));

        if (jwtService.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak expirée.");
        }

        Long utilisateurId = jwtService.extractUserId(token);
        if (utilisateurId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token Keycloak invalide.");
        }

        utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session introuvable."));

        if (jwtService.isSessionInvalidated(token, utilisateur)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session Keycloak invalidée.");
        }

        if (utilisateur.getRole() != RoleUser.SUPERVISEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès superviseur requis.");
        }
    }

    private String resolvePdvName(SwitchMonetiqueClient.SwitchTransaction transaction) {
        if (!StringUtils.hasText(transaction.idTpe())) {
            return "";
        }
        return switchMonetiqueClient.parId(transaction.idTpe())
            .map(SwitchMonetiqueClient.SwitchTpe::idPdv)
            .filter(StringUtils::hasText)
            .flatMap(this::findPdvById)
            .map(pdv::getNomPDV)
            .orElse("");
    }

    private Optional<pdv> findPdvById(String idPdv) {
        try {
            return pdvRepository.findById(Long.valueOf(idPdv));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /**
     * Reproduit le format d'un vrai ticket TPE physique LanaCash (papier
     * thermique 80mm : "TICKET COMMERÇANT À CONSERVER", logo, ACHAT,
     * AID EMV, numero de carte masque + schema, MONTANT, NUM TRANSACTION /
     * NUM AUTORISATION / STAN, "TICKET CLIENT") plutot que le "REÇU DE
     * PAIEMENT" generique precedent — voir stan/rrn/codeAutorisation/
     * panMasque/typeCarte/aid dans SwitchMonetiqueClient.SwitchTransaction
     * (ISO8583 reel, AuthorizationService cote switch-monetique-service).
     * N'affiche que des champs reellement disponibles : pas de numero de
     * sequence/lot fictif, pas de code d'affiliation invente — un champ
     * absent (ex. panMasque sur une vieille transaction ou un canal
     * e-commerce) est simplement omis plutot que remplace par une valeur
     * plausible-mais-fausse.
     */
    private String buildTicketHtml(
        SwitchMonetiqueClient.SwitchTransaction transaction,
        String merchantName,
        String pdvName,
        String merchantVille
    ) {
        boolean isEcommerce = "ECOMMERCE".equalsIgnoreCase(transaction.canal());
        LocalDateTime date = transaction.dateTransaction();
        boolean isAchat = "ACHAT".equalsIgnoreCase(safe(transaction.typeTransaction()));

        StringBuilder rows = new StringBuilder();
        appendRow(rows, "Date", date == null ? "" : date.format(TICKET_DATE_ONLY_FORMAT));
        appendRow(rows, "Heure", date == null ? "" : date.format(TICKET_TIME_ONLY_FORMAT));
        if (StringUtils.hasText(transaction.aid())) {
            appendRow(rows, "AID", transaction.aid());
        }
        if (StringUtils.hasText(transaction.panMasque())) {
            String carte = (isAchat ? "DEBIT " : "") + safe(transaction.typeCarte());
            appendRow(rows, transaction.panMasque(), carte.trim());
        }
        if (!isEcommerce && StringUtils.hasText(transaction.idTpe())) {
            appendRow(rows, "Terminal", transaction.idTpe());
        }
        if (isEcommerce && StringUtils.hasText(transaction.idSiteEcommerce())) {
            appendRow(rows, "Site e-commerce", transaction.idSiteEcommerce());
        }

        StringBuilder refRows = new StringBuilder();
        appendRow(refRows, "N° TRANSACTION", safe(transaction.idTransaction()));
        if (StringUtils.hasText(transaction.codeAutorisation())) {
            appendRow(refRows, "N° AUTORISATION", transaction.codeAutorisation());
        }
        if (StringUtils.hasText(transaction.stan())) {
            appendRow(refRows, "STAN", transaction.stan());
        }
        if (StringUtils.hasText(transaction.rrn())) {
            appendRow(refRows, "RRN", transaction.rrn());
        }

        return """
            <html>
            <head>
              <meta charset="UTF-8" />
              <style>
                @page { size: 80mm 150mm; margin: 0; }
                * { box-sizing: border-box; }
                body {
                  font-family: 'Courier New', Courier, monospace; color: #111827; margin: 0;
                  padding: 7mm 6mm; background: #ffffff; font-size: 10.5px; line-height: 1.5;
                }
                .banner { text-align: center; font-size: 9px; font-weight: 700; letter-spacing: .5px;
                  border-bottom: 1px dashed #9ca3af; padding-bottom: 6px; margin-bottom: 8px; }
                .head { text-align: center; }
                .logo { display: block; margin: 0 auto 6px; height: 30px; }
                .achat-line { text-align: center; font-size: 12px; font-weight: 700; letter-spacing: 1px;
                  margin: 4px 0 10px; }
                .rule { border: none; border-top: 1px solid #111827; margin: 8px 0; }
                .rule-dashed { border: none; border-top: 1px dashed #9ca3af; margin: 8px 0; }
                .merchant-block { text-align: center; margin-bottom: 8px; }
                .merchant-block .name { font-weight: 700; }
                table { width: 100%%; border-collapse: collapse; font-size: 10px; }
                td { padding: 2px 0; vertical-align: top; }
                td.label { color: #374151; width: 46%%; }
                td.value { font-weight: 700; text-align: right; }
                .amount-row td { padding-top: 8px; font-size: 13px; }
                .amount-row td.label { color: #111827; font-weight: 700; }
                .amount-row td.value { font-size: 15px; }
                .status-line { text-align: center; margin: 10px 0 2px; font-size: 11px; font-weight: 700;
                  letter-spacing: 1px; color: %s; }
                .footer { text-align: center; font-size: 9px; font-weight: 700; letter-spacing: .5px;
                  border-top: 1px dashed #9ca3af; padding-top: 8px; margin-top: 10px; }
              </style>
            </head>
            <body>
              <p class="banner">TICKET COMMERÇANT À CONSERVER</p>
              <div class="head">
                <img class="logo" src="%s" />
              </div>
              <p class="achat-line">%s )))</p>
              <div class="merchant-block">
                <div class="name">%s</div>
                <div>%s</div>
              </div>
              <hr class="rule" />
              <table>%s</table>
              <hr class="rule-dashed" />
              <table>
                <tr class="amount-row"><td class="label">MONTANT</td><td class="value">%s %s</td></tr>
              </table>
              <hr class="rule-dashed" />
              <table>%s</table>
              <p class="status-line">*** %s ***</p>
              <p class="footer">TICKET CLIENT</p>
            </body>
            </html>
            """.formatted(
                statusColor(transaction.statut()),
                pdfLogoProvider.lanaCash(),
                isAchat ? "ACHAT" : safe(transaction.typeTransaction()),
                escapeHtml(StringUtils.hasText(merchantName) ? merchantName : "—"),
                escapeHtml(firstNotBlank(pdvName, merchantVille)),
                rows,
                formatMontant(transaction.montant()),
                safe(transaction.devise()),
                refRows,
                translateStatut(transaction.statut())
            );
    }

    private void appendRow(StringBuilder rows, String label, String value) {
        rows.append("<tr><td class=\"label\">")
            .append(escapeHtml(label))
            .append("</td><td class=\"value\">")
            .append(escapeHtml(StringUtils.hasText(value) ? value : "—"))
            .append("</td></tr>");
    }

    private String statusColor(String statut) {
        String normalized = safe(statut).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "#16a34a";
            case "DECLINED" -> "#dc3545";
            case "TIMEOUT", "EXPIRED" -> "#f59e0b";
            default -> "#6b7f91";
        };
    }

    private String translateStatut(String statut) {
        String normalized = safe(statut).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "APPROUVÉE";
            case "DECLINED" -> "REFUSÉE";
            case "TIMEOUT" -> "EXPIRÉE (délai)";
            case "EXPIRED" -> "EXPIRÉE";
            case "REVERSED" -> "ANNULÉE";
            case "PENDING" -> "EN ATTENTE";
            default -> normalized.isEmpty() ? "—" : normalized;
        };
    }

    private String formatMontant(BigDecimal montant) {
        return montant == null ? "0.00" : montant.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private byte[] renderPdf(String htmlContent) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Impossible de générer le ticket de transaction.",
                exception
            );
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
