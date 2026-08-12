package com.example.demo.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Géocode une adresse marocaine (adresse, quartier, ville, code postal) en
 * coordonnées GPS via l'API publique Nominatim (OpenStreetMap). Best-effort :
 * ne lève jamais d'exception — un échec de géocodage ne doit jamais bloquer
 * la création d'un point de vente, seulement laisser ses coordonnées vides.
 */
@Service
public class GeocodingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeocodingService.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String userAgent;

    public GeocodingService(
        RestClient.Builder restClientBuilder,
        @Value("${app.geocoding.nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
        @Value("${app.geocoding.user-agent:LanaCashPortailAffiliation/1.0}") String userAgent
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
    }

    // Prefixes administratifs frequents dans la nomenclature officielle des quartiers
    // (Barid Al-Maghrib) mais absents de la toponymie OpenStreetMap - les garder dans
    // la requete de secours fait systematiquement echouer la recherche en texte libre.
    private static final Pattern PREFIXE_ADMINISTRATIF = Pattern.compile(
        "(?i)^(quartier|derb|hay|lotissement|residence|cite|douar)\\s+"
    );

    public record Coordonnees(double latitude, double longitude) {
    }

    /**
     * Tente le géocodage avec l'adresse complète, puis se rabat progressivement sur
     * des combinaisons plus courtes (adresse seule, quartier seul sans son préfixe
     * administratif) si Nominatim ne trouve rien - la recherche en texte libre de
     * Nominatim échoue souvent dès qu'une chaîne combine trop de composants ou des
     * libellés officiels absents d'OpenStreetMap. On ne descend jamais en dessous du
     * niveau quartier+ville : une position au niveau ville seule n'est pas acceptable.
     */
    public Optional<Coordonnees> geocoder(String adresse, String quartier, String ville, String codePostal) {
        List<String> tentatives = buildQueryCandidates(adresse, quartier, ville, codePostal);
        for (int index = 0; index < tentatives.size(); index++) {
            Optional<Coordonnees> resultat = interroger(tentatives.get(index));
            if (resultat.isPresent()) {
                return resultat;
            }
            if (index < tentatives.size() - 1) {
                attendreEntreDeuxAppels();
            }
        }
        return Optional.empty();
    }

    private Optional<Coordonnees> interroger(String query) {
        try {
            String uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/search")
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .queryParam("countrycodes", "ma")
                .queryParam("q", query)
                .build()
                .toUriString();

            List<Map<String, Object>> results = restClient
                .get()
                .uri(uri)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "fr")
                .retrieve()
                .body(List.class);

            if (results == null || results.isEmpty()) {
                LOGGER.info("Géocodage sans résultat pour : {}", query);
                return Optional.empty();
            }

            Map<String, Object> premier = results.get(0);
            Double latitude = parseCoordinate(premier.get("lat"));
            Double longitude = parseCoordinate(premier.get("lon"));
            if (latitude == null || longitude == null) {
                return Optional.empty();
            }

            return Optional.of(new Coordonnees(latitude, longitude));
        } catch (RestClientException exception) {
            LOGGER.warn("Géocodage impossible pour '{}' : {}", query, exception.getMessage());
            return Optional.empty();
        } catch (Exception exception) {
            LOGGER.warn("Erreur inattendue lors du géocodage de '{}'", query, exception);
            return Optional.empty();
        }
    }

    private void attendreEntreDeuxAppels() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> buildQueryCandidates(String adresse, String quartier, String ville, String codePostal) {
        Set<String> candidats = new LinkedHashSet<>();
        addCandidateIfMeaningful(candidats, adresse, quartier, ville, codePostal);
        addCandidateIfMeaningful(candidats, adresse, null, ville, codePostal);
        String quartierSansPrefixe = stripPrefixeAdministratif(quartier);
        addCandidateIfMeaningful(candidats, null, quartierSansPrefixe, ville, null);
        addCandidateIfMeaningful(candidats, null, quartier, ville, null);
        return List.copyOf(candidats);
    }

    private void addCandidateIfMeaningful(
        Set<String> candidats,
        String adresse,
        String quartier,
        String ville,
        String codePostal
    ) {
        if (!StringUtils.hasText(ville) && !StringUtils.hasText(quartier) && !StringUtils.hasText(adresse)) {
            return;
        }
        String query = buildQuery(adresse, quartier, ville, codePostal);
        if (StringUtils.hasText(query)) {
            candidats.add(query);
        }
    }

    private String stripPrefixeAdministratif(String quartier) {
        if (!StringUtils.hasText(quartier)) {
            return quartier;
        }
        String sansPrefixe = PREFIXE_ADMINISTRATIF.matcher(quartier.trim()).replaceFirst("");
        return sansPrefixe.equalsIgnoreCase(quartier.trim()) ? null : sansPrefixe;
    }

    private String buildQuery(String adresse, String quartier, String ville, String codePostal) {
        StringJoiner joiner = new StringJoiner(", ");
        appendIfPresent(joiner, adresse);
        appendIfPresent(joiner, quartier);
        appendIfPresent(joiner, ville);
        appendIfPresent(joiner, codePostal);
        joiner.add("Maroc");
        return joiner.toString();
    }

    private void appendIfPresent(StringJoiner joiner, String value) {
        if (StringUtils.hasText(value)) {
            joiner.add(value.trim());
        }
    }

    private Double parseCoordinate(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
