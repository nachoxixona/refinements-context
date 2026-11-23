/**
 * Spec cards module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Spec cards",
        allowedDependencies = {"shared"})
package com.refinements.visualizer.card;

import org.springframework.modulith.ApplicationModule;
