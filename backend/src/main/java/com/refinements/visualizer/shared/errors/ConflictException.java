package com.refinements.visualizer.shared.errors;

import java.util.Map;

/**
 * 409. {@code conflict} is a machine-readable code from contracts/openapi.yaml {@code ConflictProblem} or
 * {@code DuplicateCardProblem}; {@code properties} become extra Problem Details members.
 */
public class ConflictException extends RuntimeException {

    public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
    public static final String DUPLICATE_CONNECTION = "DUPLICATE_CONNECTION";
    public static final String SELF_CONNECTION = "SELF_CONNECTION";
    public static final String MICROSERVICE_HAS_CARD = "MICROSERVICE_HAS_CARD";
    public static final String CARD_ALREADY_LINKED = "CARD_ALREADY_LINKED";
    public static final String DUPLICATE_SCENARIO_NAME = "DUPLICATE_SCENARIO_NAME";
    public static final String DUPLICATE_CARD = "DUPLICATE_CARD";

    private final String conflict;
    private final Map<String, Object> properties;

    public ConflictException(String message, String conflict) {
        this(message, conflict, Map.of());
    }

    public ConflictException(String message, String conflict, Map<String, Object> properties) {
        super(message);
        this.conflict = conflict;
        this.properties = Map.copyOf(properties);
    }

    public String getConflict() {
        return conflict;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
