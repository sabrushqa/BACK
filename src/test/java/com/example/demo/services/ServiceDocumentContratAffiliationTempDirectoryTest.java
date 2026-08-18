package com.example.demo.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Couvre createRestrictedTempDirectory() (Sonar S5443 : le repertoire temp
 * utilise pour generer un contrat via Chromium doit rester inaccessible aux
 * autres utilisateurs locaux — cf. renderPdfWithChrome). Teste directement
 * (visibilite package) sans passer par le rendu Chromium reel, qui necessite
 * un binaire Chrome installe et n'est donc pas exerce par les autres tests.
 */
class ServiceDocumentContratAffiliationTempDirectoryTest {

    @TempDir
    Path tempDirectory;

    private ServiceDocumentContratAffiliation buildService() {
        GenerateurModeleContratAffiliation templateRenderer =
            new GenerateurModeleContratAffiliation(null, null, null, null, new PdfLogoProvider());
        return new ServiceDocumentContratAffiliation(
            templateRenderer,
            null,
            tempDirectory.toString(),
            false,
            ""
        );
    }

    @Test
    void createsDirectoryRestrictedToOwnerOnPosixFileSystems() throws IOException {
        ServiceDocumentContratAffiliation service = buildService();

        Path created = service.createRestrictedTempDirectory("lana-contract-render-test-");

        assertThat(Files.exists(created)).isTrue();
        assertThat(Files.isDirectory(created)).isTrue();

        // La JVM de test tourne toujours sur un OS POSIX (macOS/Linux) en CI/local
        // pour ce depot — la branche de repli Windows (UnsupportedOperationException)
        // n'est donc pas exerçable ici sans OS Windows reel, mais la protection reelle
        // (permissions restreintes) est bien celle-ci qu'on verifie.
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(created);
        assertThat(permissions).isEqualTo(EnumSet.copyOf(PosixFilePermissions.fromString("rwx------")));
    }

    @Test
    void createsDistinctDirectoriesOnEachCall() throws IOException {
        ServiceDocumentContratAffiliation service = buildService();

        Path first = service.createRestrictedTempDirectory("lana-contract-render-test-");
        Path second = service.createRestrictedTempDirectory("lana-contract-render-test-");

        assertThat(first).isNotEqualTo(second);
    }
}
