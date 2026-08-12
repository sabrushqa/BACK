package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Verifie le formatage lisible des limites d'upload: exact en GB, exact en
 * MB, et repli en octets bruts quand la taille ne tombe pas rond sur une
 * unite superieure.
 */
class UploadLimitsPropertiesTest {

    @Test
    void formatsExactGigabytesLabel() {
        UploadLimitsProperties properties = new UploadLimitsProperties();
        properties.setMaxRequestSize(DataSize.ofGigabytes(2));

        assertThat(properties.getMaxRequestSizeLabel()).isEqualTo("2 GB");
    }

    @Test
    void formatsExactMegabytesLabel() {
        UploadLimitsProperties properties = new UploadLimitsProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(25));

        assertThat(properties.getMaxFileSizeLabel()).isEqualTo("25 MB");
    }

    @Test
    void formatsRawBytesWhenNotRoundToAnyUnit() {
        UploadLimitsProperties properties = new UploadLimitsProperties();
        properties.setMaxFileSize(DataSize.ofBytes(1500));

        assertThat(properties.getMaxFileSizeLabel()).isEqualTo("1500 octets");
    }

    @Test
    void exposesConfiguredMultipartPartsLimit() {
        UploadLimitsProperties properties = new UploadLimitsProperties();
        properties.setMaxMultipartParts(42);

        assertThat(properties.getMaxMultipartParts()).isEqualTo(42);
    }
}
