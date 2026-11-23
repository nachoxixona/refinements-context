/**
 * Scenarios module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Scenarios",
        allowedDependencies = {"shared", "canvas", "card :: model", "connection", "note"})
package com.refinements.visualizer.scenario;

import org.springframework.modulith.ApplicationModule;
