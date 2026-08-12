package com.example.demo.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valide le type MIME réel d'un fichier uploadé en lisant ses magic bytes,
 * indépendamment de l'extension ou du Content-Type déclaré par le client.
 */
@Component
public class DocumentMimeValidator {

    private static final int HEADER_BYTES = 8;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png", "gif", "tiff", "tif", "docx", "doc"
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String ext = extension(originalName);
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException(
                    "Type de fichier non autorisé : ." + ext
                    + ". Formats acceptés : PDF, JPEG, PNG, GIF, TIFF, DOCX."
                );
            }
        }

        byte[] header;
        try (InputStream is = file.getInputStream()) {
            header = is.readNBytes(HEADER_BYTES);
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le fichier uploadé.");
        }

        if (!matchesMagicBytes(header)) {
            throw new IllegalArgumentException(
                "Le contenu du fichier ne correspond pas à son extension. "
                + "Seuls les vrais PDF, images et documents Word sont acceptés."
            );
        }
    }

    private static boolean matchesMagicBytes(byte[] h) {
        if (h == null || h.length < 4) return false;

        // PDF : %PDF
        if (h[0] == 0x25 && h[1] == 0x50 && h[2] == 0x44 && h[3] == 0x46) return true;

        // JPEG : FF D8 FF
        if (h[0] == (byte) 0xFF && h[1] == (byte) 0xD8 && h[2] == (byte) 0xFF) return true;

        // PNG : 89 50 4E 47 0D 0A 1A 0A
        if (h.length >= 8
                && h[0] == (byte) 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) return true;

        // GIF87a / GIF89a
        if (h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38) return true;

        // TIFF little-endian : II 2A 00
        if (h[0] == 0x49 && h[1] == 0x49 && h[2] == 0x2A && h[3] == 0x00) return true;
        // TIFF big-endian : MM 00 2A
        if (h[0] == 0x4D && h[1] == 0x4D && h[2] == 0x00 && h[3] == 0x2A) return true;

        // ZIP-based formats (DOCX, XLSX, ODP, etc.) : PK 03 04
        if (h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04) return true;

        // DOC legacy (OLE2) : D0 CF 11 E0
        if (h[0] == (byte) 0xD0 && h[1] == (byte) 0xCF && h[2] == 0x11 && h[3] == (byte) 0xE0) return true;

        return false;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
