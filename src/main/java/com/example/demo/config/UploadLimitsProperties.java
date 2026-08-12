package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadLimitsProperties {

    private DataSize maxFileSize = DataSize.ofMegabytes(250);
    private DataSize maxRequestSize = DataSize.ofGigabytes(1);
    private int maxMultipartParts = -1;

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public DataSize getMaxRequestSize() {
        return maxRequestSize;
    }

    public void setMaxRequestSize(DataSize maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    public String getMaxFileSizeLabel() {
        return format(maxFileSize);
    }

    public String getMaxRequestSizeLabel() {
        return format(maxRequestSize);
    }

    public int getMaxMultipartParts() {
        return maxMultipartParts;
    }

    public void setMaxMultipartParts(int maxMultipartParts) {
        this.maxMultipartParts = maxMultipartParts;
    }

    private String format(DataSize size) {
        long bytes = size.toBytes();
        long gigaByte = DataSize.ofGigabytes(1).toBytes();
        long megaByte = DataSize.ofMegabytes(1).toBytes();

        if (bytes >= gigaByte && bytes % gigaByte == 0) {
            return (bytes / gigaByte) + " GB";
        }

        if (bytes >= megaByte && bytes % megaByte == 0) {
            return (bytes / megaByte) + " MB";
        }

        return bytes + " octets";
    }
}
