/**
 * Shared kernel: errors, the patch helper, positions, name rules and configuration. Open module, usable by every
 * other module (specs/005-modular-backend research R3).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")
package com.refinements.visualizer.shared;

import org.springframework.modulith.ApplicationModule;
