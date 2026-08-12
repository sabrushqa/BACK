package com.example.demo.services;

import com.example.demo.dto.BackofficeReclamationResponse;
import com.example.demo.dto.ReclamationDashboardResponse;
import com.example.demo.dto.ReclamationRequest;
import com.example.demo.dto.ReclamationResponse;
import com.example.demo.entities.back_office;
import com.example.demo.entities.commercant;
import com.example.demo.entities.dossier_affiliation;
import com.example.demo.entities.Reclamation;
import com.example.demo.entities.tpe;
import com.example.demo.entities.utilisateur;
import com.example.demo.enums.RoleUser;
import com.example.demo.repositories.BackOfficeRepository;
import com.example.demo.repositories.CommercantRepository;
import com.example.demo.repositories.DossierAffiliationRepository;
import com.example.demo.repositories.ReclamationRepository;
import com.example.demo.repositories.TpeRepository;
import com.example.demo.repositories.UtilisateurRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReclamationService {

    private final ReclamationRepository reclamationRepository;
    private final CommercantRepository  commercantRepository;
    private final TpeRepository         tpeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final BackOfficeRepository  backOfficeRepository;
    private final DossierAffiliationRepository dossierAffiliationRepository;
    private final JwtService            jwtService;

    public ReclamationService(
        ReclamationRepository reclamationRepository,
        CommercantRepository  commercantRepository,
        TpeRepository         tpeRepository,
        UtilisateurRepository utilisateurRepository,
        BackOfficeRepository  backOfficeRepository,
        DossierAffiliationRepository dossierAffiliationRepository,
        JwtService            jwtService
    ) {
        this.reclamationRepository = reclamationRepository;
        this.commercantRepository  = commercantRepository;
        this.tpeRepository         = tpeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.backOfficeRepository  = backOfficeRepository;
        this.dossierAffiliationRepository = dossierAffiliationRepository;
        this.jwtService            = jwtService;
    }

    public ReclamationResponse createReclamation(String authHeader, ReclamationRequest req) {
        commercant merchant = resolveCommercant(authHeader);

        // Dedup : v2 (agent/tools/ticket_tool.py) peut deja avoir persiste ce
        // meme ticket (meme referenceChat) via /api/chatbot/reclamations avant
        // que le front n'appelle cet endpoint — on enrichit la ligne existante
        // (commercant/tpe resolus cote JWT, plus fiables) au lieu d'en creer une seconde.
        Reclamation rec = reclamationRepository.findByReferenceChat(req.referenceChat())
            .orElseGet(Reclamation::new);
        boolean isNew = rec.getIdReclamation() == null;

        if (isNew) {
            rec.setDateCreation(LocalDate.now());
            rec.setReferenceChat(req.referenceChat());
            rec.setStatut("EN_ATTENTE");
        }
        rec.setTypeProbleme(normalizeType(req.typeProbleme()));
        rec.setDescription(req.description());
        rec.setPriorite(normalizePriority(req.priorite()));
        rec.setCommentaire(req.commentaire());
        rec.setCommercant(merchant);

        if (req.tpeId() != null && rec.getTpe() == null) {
            tpeRepository.findAssignedToCommercant(req.tpeId(), merchant.getIdCommercant())
                .ifPresent(rec::setTpe);
        }
        if (req.tpeReference() != null && rec.getTpeReference() == null) {
            rec.setTpeReference(req.tpeReference());
        }

        Reclamation saved = reclamationRepository.save(rec);
        return toResponse(saved);
    }

    public List<ReclamationResponse> getMyReclamations(String authHeader) {
        commercant merchant = resolveCommercant(authHeader);
        return reclamationRepository
            .findByCommercant_IdCommercantOrderByDateCreationDesc(merchant.getIdCommercant())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    // ── Backoffice methods ────────────────────────────────────────────────────

    public List<BackofficeReclamationResponse> getAllReclamations(
        String authHeader,
        String statut,
        String priorite,
        String type
    ) {
        requireReclamationPermission(readAuthenticatedStaff(authHeader, RoleUser.SUPERVISEUR, RoleUser.BACK_OFFICE));
        List<Reclamation> results;
        if (statut   != null) results = reclamationRepository.findByStatutOrderByDateCreationDescIdReclamationDesc(statut);
        else if (priorite != null) results = reclamationRepository.findByPrioriteOrderByDateCreationDescIdReclamationDesc(priorite);
        else if (type != null) results = reclamationRepository.findByTypeProblemeOrderByDateCreationDescIdReclamationDesc(type);
        else results = reclamationRepository.findAllByOrderByDateCreationDescIdReclamationDesc();
        return results.stream().map(this::toBackofficeResponse).toList();
    }

    public Map<String, Long> getStats(String authHeader) {
        utilisateur authenticatedUser = readAuthenticatedStaff(authHeader, RoleUser.SUPERVISEUR, RoleUser.BACK_OFFICE);
        requireReclamationPermission(authenticatedUser);
        List<Reclamation> scoped = scopeReclamationsForBackOfficeDashboard(
            reclamationRepository.findAllByOrderByDateCreationDescIdReclamationDesc(),
            authenticatedUser
        );
        return Map.ofEntries(
            Map.entry("total",        (long) scoped.size()),
            Map.entry("EN_COURS",     countByStatut(scoped, "EN_COURS")),
            Map.entry("EN_ATTENTE",   countByStatut(scoped, "EN_ATTENTE")),
            Map.entry("RESOLU",       countByStatut(scoped, "RESOLU")),
            Map.entry("ESCALADE",     countByStatut(scoped, "ESCALADE")),
            Map.entry("CRITIQUE",     countByPriorite(scoped, "CRITIQUE")),
            Map.entry("HAUTE",        countByPriorite(scoped, "HAUTE")),
            Map.entry("CONNECTIVITE", countByTypeProbleme(scoped, "CONNECTIVITE")),
            Map.entry("TRANSACTION",  countByTypeProbleme(scoped, "TRANSACTION")),
            Map.entry("MATERIEL",     countByTypeProbleme(scoped, "MATERIEL")),
            Map.entry("LOGICIEL",     countByTypeProbleme(scoped, "LOGICIEL")),
            Map.entry("RESEAU",       countByTypeProbleme(scoped, "RESEAU")),
            Map.entry("AUTRE",        countByTypeProbleme(scoped, "AUTRE"))
        );
    }

    public ReclamationDashboardResponse getDashboard(String authHeader, Integer days, String type) {
        utilisateur authenticatedUser = readAuthenticatedStaff(authHeader, RoleUser.SUPERVISEUR, RoleUser.BACK_OFFICE);
        requireReclamationPermission(authenticatedUser);

        int rangeDays = (days == null || days < 1) ? 7 : Math.min(days, 90);

        List<Reclamation> all = scopeReclamationsForBackOfficeDashboard(
            reclamationRepository.findAllByOrderByDateCreationDescIdReclamationDesc(),
            authenticatedUser
        );
        if (StringUtils.hasText(type)) {
            all = all.stream().filter(r -> type.equals(r.getTypeProbleme())).toList();
        }
        LocalDate today = LocalDate.now();

        LocalDate rangeStart = today.minusDays(rangeDays - 1L);
        Map<LocalDate, List<Reclamation>> byDay = all.stream()
            .filter(r -> r.getDateCreation() != null && !r.getDateCreation().isBefore(rangeStart))
            .collect(Collectors.groupingBy(Reclamation::getDateCreation));
        List<ReclamationDashboardResponse.DayCount> parJour = new java.util.ArrayList<>();
        for (LocalDate d = rangeStart; !d.isAfter(today); d = d.plusDays(1)) {
            List<Reclamation> dayItems = byDay.getOrDefault(d, List.of());
            parJour.add(new ReclamationDashboardResponse.DayCount(
                d,
                dayItems.size(),
                dayItems.stream().filter(r -> "EN_ATTENTE".equals(r.getStatut())).count(),
                dayItems.stream().filter(r -> "EN_COURS".equals(r.getStatut())).count(),
                dayItems.stream().filter(r -> "RESOLU".equals(r.getStatut())).count(),
                dayItems.stream().filter(r -> "ESCALADE".equals(r.getStatut())).count()
            ));
        }

        Map<String, Long> parEtat = Map.ofEntries(
            Map.entry("EN_COURS",   all.stream().filter(r -> "EN_COURS".equals(r.getStatut())).count()),
            Map.entry("EN_ATTENTE", all.stream().filter(r -> "EN_ATTENTE".equals(r.getStatut())).count()),
            Map.entry("RESOLU",     all.stream().filter(r -> "RESOLU".equals(r.getStatut())).count()),
            Map.entry("ESCALADE",   all.stream().filter(r -> "ESCALADE".equals(r.getStatut())).count())
        );

        List<Reclamation> overdue = all.stream()
            .filter(r -> "EN_COURS".equals(r.getStatut()) || "EN_ATTENTE".equals(r.getStatut()))
            .filter(r -> r.getDateCreation() != null && r.getDateCreation().isBefore(today.minusDays(3)))
            .sorted((a, b) -> a.getDateCreation().compareTo(b.getDateCreation()))
            .toList();

        List<ReclamationDashboardResponse.OverdueItem> enRetard = overdue.stream()
            .limit(20)
            .map(r -> new ReclamationDashboardResponse.OverdueItem(
                r.getIdReclamation(),
                r.getReferenceChat(),
                r.getTypeProbleme(),
                r.getStatut(),
                r.getCommercant() == null ? null : r.getCommercant().getNomCommercial(),
                r.getDateCreation(),
                java.time.temporal.ChronoUnit.DAYS.between(r.getDateCreation(), today)
            ))
            .toList();

        return new ReclamationDashboardResponse(parJour, parEtat, overdue.size(), enRetard);
    }

    public BackofficeReclamationResponse updateStatut(String authHeader, Long id, String newStatut) {
        requireReclamationPermission(readAuthenticatedStaff(authHeader, RoleUser.SUPERVISEUR, RoleUser.BACK_OFFICE));
        Reclamation rec = reclamationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réclamation introuvable: " + id));
        rec.setStatut(newStatut);
        if ("RESOLU".equals(newStatut) || "ESCALADE".equals(newStatut)) {
            rec.setDateResolution(LocalDate.now());
            resolveBackOffice(authHeader).ifPresent(rec::setBackOffice);
        }
        return toBackofficeResponse(reclamationRepository.save(rec));
    }

    private BackofficeReclamationResponse toBackofficeResponse(Reclamation r) {
        tpe t = r.getTpe();
        commercant c = r.getCommercant();
        back_office bo = r.getBackOffice();
        return new BackofficeReclamationResponse(
            r.getIdReclamation(),
            r.getReferenceChat(),
            r.getTypeProbleme(),
            r.getDescription(),
            r.getStatut(),
            r.getPriorite(),
            r.getDateCreation(),
            r.getDateResolution(),
            r.getCommentaire(),
            t != null ? t.getIdTPE()        : null,
            t != null ? t.getNumeroSerie()  : null,
            t != null ? t.getModele()       : null,
            r.getTpeReference(),
            c != null ? c.getIdCommercant()   : null,
            c != null ? c.getNomCommercial() : null,
            c != null ? c.getRegion() : null,
            resolveTypeAffiliation(c),
            resolveBackOfficeDisplayName(bo),
            bo != null ? bo.getIdBackOffice() : null,
            bo != null && bo.getUtilisateur() != null ? bo.getUtilisateur().getId() : null,
            resolveDureeTraitementJours(r)
        );
    }

    private Long resolveDureeTraitementJours(Reclamation r) {
        if (r.getDateCreation() == null || r.getDateResolution() == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(r.getDateCreation(), r.getDateResolution());
    }

    private String resolveTypeAffiliation(commercant c) {
        if (c == null || c.getIdCommercant() == null) {
            return null;
        }
        return dossierAffiliationRepository
            .findFirstByCommercant_IdCommercantOrderByDateSoumissionDescIdDossierDesc(c.getIdCommercant())
            .map(dossier_affiliation::getTypeAffiliation)
            .map(Enum::name)
            .orElse(null);
    }

    private String resolveBackOfficeDisplayName(back_office bo) {
        if (bo == null) {
            return null;
        }
        String fullName = (safe(bo.getPrenom()) + " " + safe(bo.getNom())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return bo.getUtilisateur() != null ? bo.getUtilisateur().getEmail() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<Reclamation> scopeReclamationsForBackOfficeDashboard(
        List<Reclamation> reclamations,
        utilisateur authenticatedUser
    ) {
        if (authenticatedUser.getRole() != RoleUser.BACK_OFFICE) {
            return reclamations;
        }

        Long currentUserId = authenticatedUser.getId();
        return reclamations
            .stream()
            .filter(reclamation -> {
                back_office backOffice = reclamation.getBackOffice();
                if (backOffice == null || backOffice.getUtilisateur() == null) {
                    return !isHandledReclamationStatus(reclamation.getStatut());
                }
                return currentUserId != null && currentUserId.equals(backOffice.getUtilisateur().getId());
            })
            .toList();
    }

    private boolean isHandledReclamationStatus(String statut) {
        return "RESOLU".equals(statut) || "ESCALADE".equals(statut);
    }

    private long countByStatut(List<Reclamation> reclamations, String statut) {
        return reclamations.stream().filter(r -> statut.equals(r.getStatut())).count();
    }

    private long countByPriorite(List<Reclamation> reclamations, String priorite) {
        return reclamations.stream().filter(r -> priorite.equals(r.getPriorite())).count();
    }

    private long countByTypeProbleme(List<Reclamation> reclamations, String typeProbleme) {
        return reclamations.stream().filter(r -> typeProbleme.equals(r.getTypeProbleme())).count();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private commercant resolveCommercant(String authHeader) {
        utilisateur user = resolveAuthenticatedUser(authHeader);
        return commercantRepository.findByUtilisateur_Id(user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte commerçant requis."));
    }

    private void requireReclamationPermission(utilisateur authenticatedUser) {
        if (authenticatedUser.getRole() != RoleUser.BACK_OFFICE) {
            return;
        }
        // Le compte back office doit exister (integrite de compte), mais tout agent
        // BACK_OFFICE a desormais acces complet au traitement des reclamations : la
        // restriction par permission individuelle (peutGererReclamations) a ete supprimee.
        backOfficeRepository
            .findByUtilisateur_Id(authenticatedUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte back office introuvable."));
    }

    private java.util.Optional<back_office> resolveBackOffice(String authHeader) {
        try {
            utilisateur user = resolveAuthenticatedUser(authHeader);
            return backOfficeRepository.findByUtilisateur_Id(user.getId());
        } catch (ResponseStatusException exception) {
            return java.util.Optional.empty();
        }
    }

    private utilisateur readAuthenticatedStaff(String authHeader, RoleUser... allowedRoles) {
        utilisateur utilisateur = resolveAuthenticatedUser(authHeader);
        RoleUser role = utilisateur.getRole();
        for (RoleUser allowedRole : allowedRoles) {
            if (role == allowedRole) {
                return utilisateur;
            }
        }
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Vous n'avez pas les droits necessaires pour cette action."
        );
    }

    private utilisateur resolveAuthenticatedUser(String authHeader) {
        String token = jwtService.extractBearerToken(authHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token manquant."));

        if (jwtService.isTokenExpired(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée.");
        }

        Long utilisateurId = jwtService.extractUserId(token);
        return utilisateurRepository.findById(utilisateurId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable."));
    }

    private ReclamationResponse toResponse(Reclamation r) {
        return new ReclamationResponse(
            r.getIdReclamation(),
            r.getReferenceChat(),
            r.getTypeProbleme(),
            r.getDescription(),
            r.getStatut(),
            r.getPriorite(),
            r.getDateCreation(),
            r.getDateResolution(),
            r.getCommentaire(),
            r.getTpe() != null ? r.getTpe().getNumeroSerie() : null,
            r.getTpe() != null ? r.getTpe().getModele()      : null,
            r.getTpeReference()
        );
    }

    private String normalizeType(String type) {
        if (type == null) return "AUTRE";
        return switch (type.toLowerCase()) {
            case "connectivity", "connectivite", "connectivité", "network", "reseau", "réseau" -> "CONNECTIVITE";
            case "transaction"  -> "TRANSACTION";
            case "hardware", "materiel", "matériel" -> "MATERIEL";
            case "software", "logiciel" -> "LOGICIEL";
            default -> "AUTRE";
        };
    }

    private String normalizePriority(String priorite) {
        if (priorite == null) return "MOYENNE";
        return switch (priorite.toUpperCase()) {
            case "CRITICAL", "CRITIQUE" -> "CRITIQUE";
            case "HIGH", "HAUTE"        -> "HAUTE";
            case "LOW", "BASSE"         -> "BASSE";
            default                      -> "MOYENNE";
        };
    }
}
