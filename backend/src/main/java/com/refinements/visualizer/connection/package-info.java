/**
 * Connections and flows module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Connections and flows",
        allowedDependencies = {"shared", "workspace", "microservice", "microservice :: spi", "card :: model"})
package com.refinements.visualizer.connection;

import org.springframework.modulith.ApplicationModule;
