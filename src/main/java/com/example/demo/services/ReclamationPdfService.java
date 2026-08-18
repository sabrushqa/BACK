package com.example.demo.services;

import com.example.demo.entities.Reclamation;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.ReclamationRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Génère la fiche PDF (A4, imprimable) d'une réclamation — accès BOA
 * (back-office) : visualisation en une page, message reformulé lisible
 * (description + resumeCourt produits par le chatbot, voir
 * agent/graph/nodes.py::_build_short_problem_label et
 * _build_forced_escalation_summary côté tpe-support-agent-v2), possibilité
 * d'imprimer/télécharger. Même pattern que MerchantTicketService (reçu de
 * transaction), séparé de ReclamationService pour ne pas surcharger un
 * service déjà volumineux avec la génération de document.
 */
@Service
public class ReclamationPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReclamationRepository reclamationRepository;
    private final JwtService jwtService;
    private final utilisateurAuthHelper authHelper;
    private final PdfLogoProvider pdfLogoProvider;

    public ReclamationPdfService(
        ReclamationRepository reclamationRepository,
        JwtService jwtService,
        com.example.demo.repositories.UtilisateurRepository utilisateurRepository,
        PdfLogoProvider pdfLogoProvider
    ) {
        this.reclamationRepository = reclamationRepository;
        this.jwtService = jwtService;
        this.authHelper = new utilisateurAuthHelper(jwtService, utilisateurRepository);
        this.pdfLogoProvider = pdfLogoProvider;
    }

    public record Pdf(byte[] contenu, String nomFichier) {
        // Voir MerchantContractManagementService.ContratTelecharge : equals/hashCode
        // par defaut sur un champ tableau compare des references, pas le contenu
        // (Sonar S6218).
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pdf that)) {
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
            return "Pdf[contenu=" + (contenu == null ? "null" : contenu.length + " octets")
                + ", nomFichier=" + nomFichier + "]";
        }
    }

    /** Accès BOA (SUPERVISEUR/BACK_OFFICE) — toutes réclamations. */
    public Pdf genererFiche(String authorizationHeader, Long idReclamation) {
        authHelper.requireStaff(authorizationHeader, RoleUser.SUPERVISEUR, RoleUser.BACK_OFFICE);

        Reclamation r = reclamationRepository.findById(idReclamation)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réclamation introuvable: " + idReclamation));

        return genererDepuisEntite(r);
    }

    /**
     * Rendu du PDF à partir d'une entité déjà résolue/autorisée par
     * l'appelant — utilisé par genererFiche (BOA, accès complet) ET par
     * ReclamationService::genererPdfPourCommercant (commerçant, restreint à
     * ses propres réclamations) pour ne dupliquer ni l'auth ni le rendu.
     */
    public Pdf genererDepuisEntite(Reclamation r) {
        String html = buildHtml(r);
        byte[] pdf = renderPdf(html);
        return new Pdf(pdf, "reclamation-" + safe(r.getReferenceChat(), r.getIdReclamation().toString()) + ".pdf");
    }

    private String buildHtml(Reclamation r) {
        commercant c = r.getCommercant();
        tpe t = r.getTpe();
        back_office bo = r.getBackOffice();

        StringBuilder infoRows = new StringBuilder();
        appendRow(infoRows, "Référence", safe(r.getReferenceChat()));
        appendRow(infoRows, "Type de problème", translateType(r.getTypeProbleme()));
        appendRow(infoRows, "Priorité", translatePriorite(r.getPriorite()));
        appendRow(infoRows, "Statut", translateStatut(r.getStatut()));
        appendRow(infoRows, "Date de création", formatDate(r.getDateCreation()));
        if (r.getDateResolution() != null) {
            appendRow(infoRows, "Date de résolution", formatDate(r.getDateResolution()));
        }

        StringBuilder merchantRows = new StringBuilder();
        if (c != null) {
            appendRow(merchantRows, "Commerçant", safe(firstNotBlank(c.getNomCommercial(), c.getRaisonSociale())));
            appendRow(merchantRows, "Ville", safe(c.getVille()));
            appendRow(merchantRows, "Téléphone", safe(c.getTelephone()));
        }
        if (t != null) {
            appendRow(merchantRows, "Terminal (TPE)", safe(t.getModele()));
            appendRow(merchantRows, "N° série", safe(t.getNumeroSerie()));
        } else if (StringUtils.hasText(r.getTpeReference())) {
            // TPE Oracle sans ligne locale correspondante (flux BOA principal)
            // — voir entities/Reclamation.java::tpeReference.
            appendRow(merchantRows, "Référence TPE", r.getTpeReference());
        }

        String traitantLine = bo != null
            ? "Traité par : " + safe(firstNotBlank(bo.getNom(), "")) + " " + safe(firstNotBlank(bo.getPrenom(), ""))
            : "";

        return """
            <html>
            <head>
              <meta charset="UTF-8" />
              <style>
                @page { size: A4; margin: 18mm 16mm; }
                * { box-sizing: border-box; }
                body {
                  font-family: 'Helvetica', Arial, sans-serif; color: #111827; margin: 0;
                  font-size: 11px; line-height: 1.6;
                }
                /* openhtmltopdf est un moteur CSS2.1 : pas de flexbox (voir
                   MerchantTicketService.java, meme contrainte) — mise en page
                   de l'en-tete via une table plutot que display:flex. */
                .head-table { width: 100%%; border-bottom: 2px solid #102a43;
                  padding-bottom: 10px; margin-bottom: 16px; }
                .head-table td { vertical-align: middle; padding: 0; }
                .logo { height: 32px; }
                .head-table h1 { font-size: 15px; margin: 0; color: #102a43; letter-spacing: .5px; }
                .head-table .ref { font-size: 10px; color: #6b7f91; margin-top: 2px; }
                .label-court { display: inline-block; background: #edf6fd; color: #102a43;
                  border: 1px solid #cbdbe7; border-radius: 999px; padding: 5px 12px;
                  font-size: 11px; font-weight: 700; margin-bottom: 14px; }
                h2 { font-size: 11px; text-transform: uppercase; letter-spacing: .5px;
                  color: #6b7f91; border-bottom: 1px solid #e5e7eb; padding-bottom: 4px;
                  margin: 18px 0 8px; }
                table { width: 100%%; border-collapse: collapse; margin-bottom: 4px; }
                td { padding: 4px 0; vertical-align: top; }
                td.label { color: #6b7f91; width: 32%%; }
                td.value { font-weight: 600; }
                .box { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 6px;
                  padding: 12px 14px; white-space: pre-wrap; }
                .footer { margin-top: 24px; padding-top: 10px; border-top: 1px solid #e5e7eb;
                  font-size: 9px; color: #6b7f91; }
              </style>
            </head>
            <body>
              <table class="head-table">
                <tr>
                  <td>
                    <h1>FICHE DE RÉCLAMATION</h1>
                    <div class="ref">Référence %s</div>
                  </td>
                  <td style="text-align: right;"><img class="logo" src="%s" /></td>
                </tr>
              </table>

              %s

              <h2>Informations</h2>
              <table>%s</table>

              <h2>Commerçant / Terminal</h2>
              <table>%s</table>

              <h2>Description</h2>
              <div class="box">%s</div>

              %s

              <div class="footer">
                Document généré depuis le portail d'affiliation LanaCash. %s
              </div>
            </body>
            </html>
            """.formatted(
                escapeHtml(safe(r.getReferenceChat())),
                pdfLogoProvider.lanaCash(),
                StringUtils.hasText(r.getResumeCourt())
                    ? "<div class=\"label-court\">" + escapeHtml(r.getResumeCourt()) + "</div>"
                    : "",
                infoRows,
                merchantRows.isEmpty() ? "<tr><td class=\"value\">—</td></tr>" : merchantRows,
                escapeHtml(safe(r.getDescription())),
                StringUtils.hasText(r.getCommentaire())
                    ? "<h2>Diagnostic technique</h2><div class=\"box\">" + escapeHtml(r.getCommentaire()) + "</div>"
                    : "",
                escapeHtml(traitantLine)
            );
    }

    private void appendRow(StringBuilder rows, String label, String value) {
        rows.append("<tr><td class=\"label\">")
            .append(escapeHtml(label))
            .append("</td><td class=\"value\">")
            .append(escapeHtml(StringUtils.hasText(value) ? value : "—"))
            .append("</td></tr>");
    }

    private String translateType(String type) {
        String normalized = safe(type).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONNECTIVITE" -> "Connectivité";
            case "TRANSACTION"  -> "Transaction";
            case "MATERIEL"     -> "Matériel";
            case "LOGICIEL"     -> "Logiciel";
            default             -> "Autre";
        };
    }

    private String translatePriorite(String p) {
        String normalized = safe(p).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CRITIQUE" -> "Critique";
            case "HAUTE"    -> "Haute";
            case "BASSE"    -> "Basse";
            default         -> "Moyenne";
        };
    }

    private String translateStatut(String statut) {
        String normalized = safe(statut).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EN_COURS"   -> "En cours";
            case "EN_ATTENTE" -> "En attente";
            case "RESOLU"     -> "Résolu";
            case "ESCALADE"   -> "Escaladé";
            default           -> normalized.isEmpty() ? "—" : normalized;
        };
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMAT);
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
                "Impossible de générer la fiche de réclamation.",
                exception
            );
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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

    /**
     * Petit helper d'authentification interne, pour reprendre exactement la
     * meme logique que ReclamationService::readAuthenticatedStaff sans
     * dupliquer resolveAuthenticatedUser ici (deja teste et utilise partout
     * ailleurs cote ReclamationService — pas expose publiquement, donc pas
     * reutilisable tel quel sans dupliquer sa logique JWT).
     */
    private static final class utilisateurAuthHelper {
        private final JwtService jwtService;
        private final com.example.demo.repositories.UtilisateurRepository utilisateurRepository;

        utilisateurAuthHelper(
            JwtService jwtService,
            com.example.demo.repositories.UtilisateurRepository utilisateurRepository
        ) {
            this.jwtService = jwtService;
            this.utilisateurRepository = utilisateurRepository;
        }

        void requireStaff(String authHeader, RoleUser... allowedRoles) {
            String token = jwtService.extractBearerToken(authHeader)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token manquant."));
            if (jwtService.isTokenExpired(token)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée.");
            }
            Long utilisateurId = jwtService.extractUserId(token);
            if (utilisateurId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide.");
            }
            utilisateur user = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session introuvable."));
            if (jwtService.isSessionInvalidated(token, user)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalidée.");
            }
            for (RoleUser allowedRole : allowedRoles) {
                if (user.getRole() == allowedRole) {
                    return;
                }
            }
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Vous n'avez pas les droits nécessaires pour cette action."
            );
        }
    }
}
