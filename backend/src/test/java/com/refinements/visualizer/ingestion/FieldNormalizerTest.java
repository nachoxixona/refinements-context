package com.refinements.visualizer.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldNormalizerTest {

    @Test
    void lowerCasesAndStripsSeparators() {
        assertThat(FieldNormalizer.normalize("customerId")).isEqualTo("customerid");
        assertThat(FieldNormalizer.normalize("customer_id")).isEqualTo("customerid");
        assertThat(FieldNormalizer.normalize("Customer-ID")).isEqualTo("customerid");
        assertThat(FieldNormalizer.normalize("customer id")).isEqualTo("customerid");
        assertThat(FieldNormalizer.normalize(null)).isEmpty();
    }
}
