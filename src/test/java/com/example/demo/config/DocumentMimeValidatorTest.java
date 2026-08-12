package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Verifie que la validation MIME s'appuie sur les magic bytes reels du
 * fichier (pas seulement l'extension declaree), pour chaque format accepte
 * et pour les cas de rejet: extension non autorisee, contenu ne
 * correspondant pas a l'extension declaree (usurpation), fichier vide/null.
 */
class DocumentMimeValidatorTest {

    private final DocumentMimeValidator validator = new DocumentMimeValidator();

    @Test
    void doesNothingWhenFileIsNull() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenFileIsEmpty() {
        MockMultipartFile empty = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]);
        assertThatCode(() -> validator.validate(empty)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "malware.exe", "application/octet-stream", new byte[] {1, 2, 3, 4}
        );

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non autorisé");
    }

    @Test
    void acceptsRealPdf() {
        byte[] content = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealJpeg() {
        byte[] content = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealPng() {
        byte[] content = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealGif() {
        byte[] content = new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "anim.gif", "image/gif", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealTiffLittleEndian() {
        byte[] content = new byte[] {0x49, 0x49, 0x2A, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "scan.tiff", "image/tiff", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealTiffBigEndian() {
        byte[] content = new byte[] {0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "scan.tif", "image/tiff", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealDocx() {
        byte[] content = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
            "file", "contrat.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            content
        );

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void acceptsRealLegacyDoc() {
        byte[] content = new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "contrat.doc", "application/msword", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredPdfExtension() {
        byte[] fakeContent = "Ceci n'est pas un vrai PDF".getBytes();
        MockMultipartFile spoofed = new MockMultipartFile("file", "faux.pdf", "application/pdf", fakeContent);

        assertThatThrownBy(() -> validator.validate(spoofed))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ne correspond pas");
    }

    @Test
    void acceptsExtensionCaseInsensitively() {
        byte[] content = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile file = new MockMultipartFile("file", "DOC.PDF", "application/pdf", content);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void treatsFilenameWithoutExtensionAsDisallowed() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "sansextension", "application/octet-stream", new byte[] {1, 2, 3, 4}
        );

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non autorisé");
    }
}
