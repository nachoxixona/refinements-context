package com.refinements.visualizer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Module structure checks (specs/005-modular-backend, FR-013). Allowed dependencies between modules are declared
 * in each module's {@code package-info.java} (contracts/module-apis.md).
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(VisualizerApplication.class);

    @Test
    @Disabled("enabled in T030, once the cycles and cross-module repository reads are gone")
    void verifiesModularStructure() {
        MODULES.verify();
    }

    /** Prints the current violations; useful while restructuring. */
    @Test
    void logsViolations() {
        System.out.println(MODULES);
        System.out.println("=== Modulith violations ===");
        System.out.println(MODULES.detectViolations().getMessages().stream()
                .map(m -> "- " + m)
                .reduce("", (a, b) -> a + b + "\n"));
    }
}
