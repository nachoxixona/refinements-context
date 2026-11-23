/**
 * Microservices module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Microservices",
        allowedDependencies = {"shared", "workspace", "card", "card :: spi"})
package com.refinements.visualizer.microservice;

import org.springframework.modulith.ApplicationModule;
