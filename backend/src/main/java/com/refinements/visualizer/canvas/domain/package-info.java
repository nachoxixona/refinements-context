/**
 * The canvas read model, composed from the other modules' views. It is returned as-is by {@code GET /api/canvas},
 * so the records keep their published Swagger {@code @Schema} names; no JPA, Spring web or Jackson here.
 */
package com.refinements.visualizer.canvas.domain;
