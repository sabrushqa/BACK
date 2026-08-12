package com.example.demo.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.config.UploadLimitsProperties;
import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests unitaires purs du gestionnaire d'exceptions global: verifie que
 * chaque type d'exception est traduit vers le bon statut HTTP et le bon
 * message, y compris les cas ou la cause reelle est enfouie dans la chaine
 * d'exceptions (upload multipart trop volumineux ou trop de fichiers).
 */
class ApiExceptionHandlerTest {

    private ApiExceptionHandler buildHandler() {
        UploadLimitsProperties uploadLimitsProperties = new UploadLimitsProperties();
        uploadLimitsProperties.setMaxFileSize(DataSize.ofMegabytes(25));
        uploadLimitsProperties.setMaxRequestSize(DataSize.ofGigabytes(1));
        uploadLimitsProperties.setMaxMultipartParts(50);
        return new ApiExceptionHandler(uploadLimitsProperties);
    }

    @Test
    void handlesMaxUploadSizeExceeded() {
        ApiExceptionHandler handler = buildHandler();

        var response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("message")).contains("25 MB");
    }

    @Test
    void handlesMaxUploadSizeExceededWithPartCountCause() {
        ApiExceptionHandler handler = buildHandler();
        FileCountLimitExceededException cause = new FileCountLimitExceededException("too many parts", 10);
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(1000, cause);

        var response = handler.handleMaxUploadSizeExceeded(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("message")).contains("10 elements multipart");
    }

    @Test
    void handlesMultipartExceptionWithPartCountCause() {
        ApiExceptionHandler handler = buildHandler();
        FileCountLimitExceededException cause = new FileCountLimitExceededException("too many parts", 5);
        MultipartException exception = new MultipartException("failed", cause);

        var response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("message")).contains("5 elements multipart");
    }

    @Test
    void handlesMultipartExceptionWithUploadSizeCause() {
        ApiExceptionHandler handler = buildHandler();
        MultipartException exception = new MultipartException(
            "failed", new IllegalStateException("the maximum upload size has been exceeded")
        );

        var response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("message")).contains("25 MB");
    }

    @Test
    void handlesMultipartExceptionWithPartCountMessageButNoExplicitLimit() {
        ApiExceptionHandler handler = buildHandler();
        // Cause dont le message evoque un depassement de nombre de fichiers, mais
        // qui n'est ni une FileCountLimitExceededException ni porteuse d'une
        // methode getLimit(): extractNumericLimit() doit alors retomber sur la
        // limite configuree (50) plutot que sur une valeur explicite.
        MultipartException exception = new MultipartException(
            "failed", new IllegalStateException("Too many files uploaded in this request")
        );

        var response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("message")).contains("50 elements multipart");
    }

    @Test
    void handlesMultipartExceptionWithGenericSizeExceededMessage() {
        ApiExceptionHandler handler = buildHandler();
        MultipartException exception = new MultipartException(
            "failed", new IllegalStateException("request size exceeds configured limit")
        );

        var response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("message")).contains("25 MB");
    }

    @Test
    void handlesMultipartExceptionWithUnrelatedCause() {
        ApiExceptionHandler handler = buildHandler();
        MultipartException exception = new MultipartException("failed", new IllegalStateException("boom"));

        var response = handler.handleMultipartException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).contains("format");
    }

    @Test
    void handlesIllegalArgumentException() {
        ApiExceptionHandler handler = buildHandler();

        var response = handler.handleBadRequest(new IllegalArgumentException("Champ obligatoire manquant."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("Champ obligatoire manquant.");
    }

    @Test
    void handlesResponseStatusExceptionWithReason() {
        ApiExceptionHandler handler = buildHandler();

        var response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé.")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("message")).isEqualTo("Accès refusé.");
    }

    @Test
    void handlesResponseStatusExceptionWithoutReason() {
        ApiExceptionHandler handler = buildHandler();

        var response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.NOT_FOUND)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("message")).isEqualTo("Erreur.");
    }
}
