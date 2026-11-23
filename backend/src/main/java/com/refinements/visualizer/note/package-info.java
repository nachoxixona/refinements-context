/**
 * Notes module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Notes",
        allowedDependencies = {"shared"})
package com.refinements.visualizer.note;

import org.springframework.modulith.ApplicationModule;
