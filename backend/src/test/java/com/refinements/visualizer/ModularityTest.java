package com.refinements.visualizer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Module structure checks (specs/005-modular-backend, FR-013 – FR-015). Allowed dependencies between modules are
 * declared in each module's {@code package-info.java}; the layout inside a module follows contracts/module-apis.md.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(VisualizerApplication.class);

    static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.refinements.visualizer");

    /** Module boundaries, declared dependencies, no cycles. */
    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    /** Ports and application never depend on adapters; primary and secondary sides stay apart (research R8). */
    @Test
    void followsHexagonalArchitecture() {
        JMoleculesArchitectureRules.ensureHexagonal().check(MAIN);
    }

    /** FR-015: the domain model is free of persistence, web and JSON frameworks. */
    @Test
    void domainIsFreeOfInfrastructure() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("com.refinements.visualizer.*.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..", "org.hibernate..", "org.springframework..", "com.fasterxml.jackson..")
                .because("domain classes are plain Java (specs/005 FR-015, contracts/module-apis.md)");
        rule.check(MAIN);
    }

    /** Template rule: a module's root package is a framework-free API (views, enums, interfaces). */
    @Test
    void moduleApisAreFrameworkFree() {
        ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(
                        "com.refinements.visualizer.card",
                        "com.refinements.visualizer.microservice",
                        "com.refinements.visualizer.connection",
                        "com.refinements.visualizer.layout",
                        "com.refinements.visualizer.note",
                        "com.refinements.visualizer.workspace",
                        "com.refinements.visualizer.canvas",
                        "com.refinements.visualizer.scenario")
                .and()
                .doNotHaveSimpleName("package-info") // carries the @ApplicationModule declaration
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.springframework..",
                        "com.fasterxml.jackson..",
                        "io.swagger..")
                .because("the root package is the module's public API (contracts/module-apis.md, template rules)");
        rule.check(MAIN);
    }

    /** Adapters are only reached through ports: application code never names an adapter class. */
    @Test
    void persistenceStaysBehindStorePorts() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..adapter.out.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.out.persistence..")
                .because("only the store adapter touches entities and repositories (research R7)")
                .check(MAIN);
    }

    /**
     * FR-019: regenerates the module documentation in {@code docs/modules/} (overview, one diagram and one canvas
     * per module). Commit the result; docs/modules/README.md explains how to read it.
     */
    @Test
    void writesDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules")).writeDocumentation();
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
