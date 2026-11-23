/**
 * Workspace module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Workspace",
        allowedDependencies = {"shared"})
package com.refinements.visualizer.workspace;

import org.springframework.modulith.ApplicationModule;
