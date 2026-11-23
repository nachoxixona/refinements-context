/**
 * Layout module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Layout",
        allowedDependencies = {"shared", "microservice", "card"})
package com.refinements.visualizer.layout;

import org.springframework.modulith.ApplicationModule;
