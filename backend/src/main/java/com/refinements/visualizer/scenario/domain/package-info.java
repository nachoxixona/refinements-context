/**
 * The scenario model: plain records and rules, no JPA, Spring web or Jackson. The content records are returned and
 * accepted as-is by the REST adapter, so they keep their Swagger {@code @Schema} names and Bean Validation
 * constraints (specs/005 data-model.md scenario).
 */
package com.refinements.visualizer.scenario.domain;
