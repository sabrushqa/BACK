package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifie le contrat "best effort" documente sur GeocodingService: un echec
 * reseau (hote injoignable) ne doit jamais faire remonter d'exception. Un
 * faux serveur HTTP local exerce en plus le chemin de succes reel (parsing
 * des coordonnees) et le repli en cascade quand Nominatim ne trouve rien.
 */
class GeocodingServiceTest {

    private HttpServer fakeNominatimServer;

    @AfterEach
    void stopFakeServer() {
        if (fakeNominatimServer != null) {
            fakeNominatimServer.stop(0);
        }
    }

    private String startFakeNominatim(String body) throws IOException {
        fakeNominatimServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeNominatimServer.createContext("/search", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });
        fakeNominatimServer.start();
        return "http://127.0.0.1:" + fakeNominatimServer.getAddress().getPort();
    }

    @Test
    void neverThrowsWhenGeocodingHostIsUnreachable() {
        GeocodingService geocodingService = new GeocodingService(
            RestClient.builder(),
            "http://127.0.0.1:1",
            "LanaCashTest/1.0"
        );

        assertThatCode(() ->
            geocodingService.geocoder("12 rue Test", null, "Casablanca", null)
        ).doesNotThrowAnyException();
    }

    @Test
    void returnsCoordinatesWhenNominatimFindsAMatch() throws IOException {
        String baseUrl = startFakeNominatim("[{\"lat\":\"33.5731\",\"lon\":\"-7.5898\"}]");
        GeocodingService geocodingService = new GeocodingService(
            RestClient.builder(), baseUrl, "LanaCashTest/1.0"
        );

        Optional<GeocodingService.Coordonnees> result =
            geocodingService.geocoder("12 rue Test", "Quartier Maarif", "Casablanca", "20000");

        assertThat(result).isPresent();
        assertThat(result.get().latitude()).isEqualTo(33.5731);
        assertThat(result.get().longitude()).isEqualTo(-7.5898);
    }

    @Test
    void returnsEmptyWhenCoordinatesAreNotNumeric() throws IOException {
        String baseUrl = startFakeNominatim("[{\"lat\":\"not-a-number\",\"lon\":\"-7.5898\"}]");
        GeocodingService geocodingService = new GeocodingService(
            RestClient.builder(), baseUrl, "LanaCashTest/1.0"
        );

        Optional<GeocodingService.Coordonnees> result =
            geocodingService.geocoder("12 rue Test", null, "Casablanca", null);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNominatimFindsNothingForAnyCandidate() throws IOException {
        String baseUrl = startFakeNominatim("[]");
        GeocodingService geocodingService = new GeocodingService(
            RestClient.builder(), baseUrl, "LanaCashTest/1.0"
        );

        Optional<GeocodingService.Coordonnees> result =
            geocodingService.geocoder("12 rue Test", "Derb Sultan", "Casablanca", "20000");

        assertThat(result).isEmpty();
    }
}
