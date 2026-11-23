package com.refinements.visualizer.shared.errors;

import java.util.List;

/** 422 with every document error; nothing was applied (FR-023). */
public class ConnectionsDocumentInvalidException extends RuntimeException {

    private final List<DocumentError> errors;

    public ConnectionsDocumentInvalidException(List<DocumentError> errors) {
        super(
                errors.size() == 1
                        ? "The connections document has 1 error. Nothing was applied."
                        : "The connections document has " + errors.size() + " errors. Nothing was applied.");
        this.errors = List.copyOf(errors);
    }

    public List<DocumentError> getErrors() {
        return errors;
    }
}
