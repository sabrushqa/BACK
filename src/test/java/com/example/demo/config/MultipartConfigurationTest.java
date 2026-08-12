package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.Http11NioProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.util.unit.DataSize;

/**
 * Verifie que le customizer Tomcat applique bien les limites d'upload
 * configurees (taille max de requete, nombre max de parametres/parts) sur un
 * connecteur reel, sans avoir besoin de demarrer un serveur complet: on
 * recupere le customizer enregistre par la factory et on l'applique
 * directement a un Connector construit pour le test.
 */
class MultipartConfigurationTest {

    @Test
    void appliesConfiguredUploadLimitsToTomcatConnector() {
        UploadLimitsProperties uploadLimitsProperties = new UploadLimitsProperties();
        uploadLimitsProperties.setMaxRequestSize(DataSize.ofMegabytes(42));
        uploadLimitsProperties.setMaxMultipartParts(77);

        MultipartConfiguration configuration = new MultipartConfiguration(uploadLimitsProperties);
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        configuration.tomcatUploadCustomizer().customize(factory);

        Connector connector = new Connector(Http11NioProtocol.class.getName());
        factory.getConnectorCustomizers().forEach(customizer -> customizer.customize(connector));

        long expectedBytes = 42L * 1024 * 1024;
        assertThat(connector.getMaxPostSize()).isEqualTo(expectedBytes);
        assertThat(connector.getMaxSavePostSize()).isEqualTo(expectedBytes);
        assertThat(connector.getMaxParameterCount()).isEqualTo(77);
        assertThat(connector.getProtocolHandler()).isInstanceOf(AbstractHttp11Protocol.class);
        AbstractHttp11Protocol<?> protocolHandler = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
        assertThat(protocolHandler.getMaxSwallowSize()).isEqualTo(expectedBytes);
    }
}
