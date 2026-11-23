package com.refinements.visualizer.common.errors;

import java.util.List;

public class SpecInvalidException extends RuntimeException {
    private final List<ParseError> errors;

    public SpecInvalidException(List<ParseError> errors) {
        super("The spec is not a valid OpenAPI/Swagger document");
        this.errors = List.copyOf(errors);
    }

    public SpecInvalidException(String location, String message) {
        this(List.of(new ParseError(location, message)));
    }

    public List<ParseError> getErrors() {
        return errors;
    }
}
