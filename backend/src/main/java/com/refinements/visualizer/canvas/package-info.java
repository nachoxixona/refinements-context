/**
 * Canvas module. Public API: the types in this package (plus named interfaces in sub-packages); everything else is
 * internal. Allowed dependencies follow specs/005-modular-backend/contracts/module-apis.md.
 */
@ApplicationModule(
        displayName = "Canvas",
        allowedDependencies = {
            "shared",
            "workspace",
            "card",
            "card :: model",
            "microservice",
            "connection",
            "layout",
            "note"
        })
package com.refinements.visualizer.canvas;

import org.springframework.modulith.ApplicationModule;
