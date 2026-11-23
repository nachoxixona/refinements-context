package com.refinements.visualizer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecKeysTest {

    @Test
    void pathParameterRenamesKeepTheEndpointKey() {
        assertThat(SpecKeys.endpointKey("card-1", "get", "/orders/{orderId}"))
                .isEqualTo("card-1:GET /orders/{}")
                .isEqualTo(SpecKeys.endpointKey("card-1", "GET", "/orders/{id}"));
        assertThat(SpecKeys.normalizePath(" ")).isEqualTo("/");
    }

    @Test
    void contentHashIgnoresLineEndingsAndSurroundingWhitespace() {
        String hash = SpecKeys.contentHash("a: 1\nb: 2\n");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(SpecKeys.contentHash("  a: 1\r\nb: 2  ")).isEqualTo(hash);
        assertThat(SpecKeys.contentHash("a: 1\nb: 3")).isNotEqualTo(hash);
    }
}
