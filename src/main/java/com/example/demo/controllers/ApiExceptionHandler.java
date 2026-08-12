package com.example.demo.controllers;

import com.example.demo.config.UploadLimitsProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final UploadLimitsProperties uploadLimitsProperties;

    public ApiExceptionHandler(UploadLimitsProperties uploadLimitsProperties) {
        this.uploadLimitsProperties = uploadLimitsProperties;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(
        MaxUploadSizeExceededException exception
    ) {
        logger.warn("Multipart upload rejected: {}", describeExceptionChain(exception));

        Long multipartPartCountLimit = findMultipartPartCountLimit(exception);
        if (multipartPartCountLimit != null) {
            return buildMultipartPartCountResponse(multipartPartCountLimit);
        }

        return buildUploadTooLargeResponse();
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipartException(
        MultipartException exception
    ) {
        logger.warn("Multipart processing failed: {}", describeExceptionChain(exception));

        Long multipartPartCountLimit = findMultipartPartCountLimit(exception);
        if (multipartPartCountLimit != null) {
            return buildMultipartPartCountResponse(multipartPartCountLimit);
        }

        if (isUploadSizeExceeded(exception)) {
            return buildUploadTooLargeResponse();
        }

        return ResponseEntity.badRequest().body(
            Map.of("message", "Impossible de traiter les fichiers envoyés. Vérifiez leur format puis réessayez.")
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
        IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
        ResponseStatusException exception
    ) {
        return ResponseEntity.status(exception.getStatusCode())
            .body(Map.of("message", exception.getReason() == null ? "Erreur." : exception.getReason()));
    }

    private ResponseEntity<Map<String, String>> buildUploadTooLargeResponse() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            Map.of(
                "message",
                "Les fichiers dépassent la taille autorisée. La limite est de "
                    + uploadLimitsProperties.getMaxFileSizeLabel()
                    + " par fichier et "
                    + uploadLimitsProperties.getMaxRequestSizeLabel()
                    + " pour toute la demande."
            )
        );
    }

    private ResponseEntity<Map<String, String>> buildMultipartPartCountResponse() {
        return buildMultipartPartCountResponse(uploadLimitsProperties.getMaxMultipartParts());
    }

    private ResponseEntity<Map<String, String>> buildMultipartPartCountResponse(long limit) {
        String limitLabel = limit < 0 ? "aucune limite explicite" : String.valueOf(limit);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            Map.of(
                "message",
                "La demande contient trop de champs ou de fichiers envoyés en une seule fois. "
                    + "La limite actuelle est de "
                    + limitLabel
                    + " elements multipart."
            )
        );
    }

    private boolean isUploadSizeExceeded(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof MaxUploadSizeExceededException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase();
                if (normalizedMessage.contains("maximum upload size")
                    || (normalizedMessage.contains("size") && normalizedMessage.contains("exceed"))) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isMultipartPartCountExceeded(Throwable throwable) {
        return findMultipartPartCountLimit(throwable) != null;
    }

    private Long findMultipartPartCountLimit(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();

            if (className.endsWith("FileCountLimitExceededException")) {
                Long explicitLimit = extractNumericLimit(current);
                return explicitLimit == null ? Long.valueOf(uploadLimitsProperties.getMaxMultipartParts()) : explicitLimit;
            }

            if (message != null) {
                String normalizedMessage = message.toLowerCase();
                if (normalizedMessage.contains("filecountlimitexceededexception")
                    || normalizedMessage.contains("too many parts")
                    || normalizedMessage.contains("too many files")) {
                    Long explicitLimit = extractNumericLimit(current);
                    return explicitLimit == null ? Long.valueOf(uploadLimitsProperties.getMaxMultipartParts()) : explicitLimit;
                }
            }

            current = current.getCause();
        }

        return null;
    }

    private Long extractNumericLimit(Throwable throwable) {
        try {
            var method = throwable.getClass().getMethod("getLimit");
            Object value = method.invoke(throwable);
            if (value instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // Ignore and fall back to configured defaults.
        }

        return null;
    }

    private String describeExceptionChain(Throwable throwable) {
        StringBuilder description = new StringBuilder();
        Throwable current = throwable;

        while (current != null) {
            if (description.length() > 0) {
                description.append(" -> ");
            }

            description.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                description.append(": ").append(current.getMessage());
            }

            current = current.getCause();
        }

        return description.toString();
    }
}
