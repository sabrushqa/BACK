package com.example.demo.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.context.annotation.Bean;

@Configuration
public class MultipartConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MultipartConfiguration.class);
    private final UploadLimitsProperties uploadLimitsProperties;

    public MultipartConfiguration(UploadLimitsProperties uploadLimitsProperties) {
        this.uploadLimitsProperties = uploadLimitsProperties;
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatUploadCustomizer() {
        return (TomcatServletWebServerFactory factory) ->
            factory.addConnectorCustomizers((Connector connector) -> {
                int maxRequestSizeBytes = toTomcatSize(uploadLimitsProperties.getMaxRequestSize());

                connector.setMaxPostSize(maxRequestSizeBytes);
                connector.setMaxSavePostSize(maxRequestSizeBytes);
                connector.setMaxParameterCount(uploadLimitsProperties.getMaxMultipartParts());

                if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocolHandler) {
                    protocolHandler.setMaxSwallowSize(maxRequestSizeBytes);
                }

                logger.info(
                    "Configured Tomcat upload limits: maxPostSize={} maxSavePostSize={} maxParameterCount={}",
                    connector.getMaxPostSize(),
                    connector.getMaxSavePostSize(),
                    connector.getMaxParameterCount()
                );
            });
    }

    private int toTomcatSize(DataSize size) {
        long bytes = size.toBytes();
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }
}
