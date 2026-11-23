package com.refinements.visualizer.microservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.shared.errors.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Microservice rules with no Spring and no database (data-model.md §2). */
class MicroserviceDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    void namesAreCleanedNormalizedAndLimited() {
        Microservice m = Microservice.create("  Order   Service ", null, NOW);
        assertThat(m.getName()).isEqualTo("Order Service");
        assertThat(m.getNameNormalized()).isEqualTo("order service");
        assertThatThrownBy(() -> Microservice.checkName("   ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Microservice.checkName("x".repeat(121))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Microservice.checkDescription("x".repeat(2001)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void ownershipIsCleanedAndUrlsMustBeHttp() {
        assertThat(Ownership.team("  Payments  ")).isEqualTo("Payments");
        assertThat(Ownership.team("   ")).isNull();
        assertThat(Ownership.url(" https://git.example.com/orders ", "repositoryUrl"))
                .isEqualTo("https://git.example.com/orders");
        assertThatThrownBy(() -> Ownership.url("ftp://example.com", "docsUrl")).isInstanceOf(BadRequestException.class);
    }
}
