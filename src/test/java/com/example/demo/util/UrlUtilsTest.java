package com.example.demo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlUtilsTest {

    @Test
    void stripsOneOrMoreTrailingSlashes() {
        assertThat(UrlUtils.stripTrailingSlash("http://localhost:8088/")).isEqualTo("http://localhost:8088");
        assertThat(UrlUtils.stripTrailingSlash("http://localhost:8088///")).isEqualTo("http://localhost:8088");
    }

    @Test
    void leavesValueWithoutTrailingSlashUnchanged() {
        assertThat(UrlUtils.stripTrailingSlash("http://localhost:8088")).isEqualTo("http://localhost:8088");
    }

    @Test
    void returnsEmptyStringForNull() {
        assertThat(UrlUtils.stripTrailingSlash(null)).isEmpty();
    }

    @Test
    void returnsEmptyStringWhenValueIsOnlySlashes() {
        assertThat(UrlUtils.stripTrailingSlash("///")).isEmpty();
    }
}
