package com.refinements.visualizer.shared.errors;

import io.swagger.v3.oas.annotations.media.Schema;

/** One connections-document error with its position (research R6, contracts/openapi.yaml DocumentError). */
@Schema(name = "DocumentError")
public record DocumentError(Kind kind, String pointer, String path, Integer line, Integer column, String message) {

    public enum Kind {
        SYNTAX,
        UNSUPPORTED_VERSION,
        WRONG_TYPE,
        MISSING,
        TOO_LONG,
        INVALID_KIND,
        SELF_CONNECTION,
        DUPLICATE_CONNECTION,
        DUPLICATE_MICROSERVICE,
        UNKNOWN_PROPERTY,
        TOO_MANY,
        UNKNOWN_FLOW,
        INVALID_COLOR,
        DUPLICATE_FLOW,
        INVALID_ENDPOINT,
        INVALID_STEP,
        STEP_WITHOUT_FLOW,
        INVALID_URL
    }
}
