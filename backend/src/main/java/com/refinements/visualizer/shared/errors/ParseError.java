package com.refinements.visualizer.shared.errors;

/** A user-facing parse problem with the location in the spec where it occurred (FR-005). */
public record ParseError(String location, String message) {}
