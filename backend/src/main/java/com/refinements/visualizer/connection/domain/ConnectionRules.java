package com.refinements.visualizer.connection.domain;

import com.refinements.visualizer.connection.ConnectionEndpoints;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;

/** Value rules for connections and flows (data-model.md §3, §3b, §3c). */
public final class ConnectionRules {

    private ConnectionRules() {}

    /** Blank → null; otherwise the normalized {@code METHOD /path} (data-model.md §3c), or 400. */
    public static String checkEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String normalized = ConnectionEndpoints.normalize(endpoint);
        if (normalized == null) {
            throw new BadRequestException("endpoint must look like 'POST /invoices' with a valid HTTP method.");
        }
        return normalized;
    }

    public static String checkName(String name) {
        String clean = Names.clean(name);
        if (clean.isEmpty()) {
            throw new BadRequestException("A connection needs a name.");
        }
        if (clean.length() > Names.MAX_NAME) {
            throw new BadRequestException("Connection names are at most " + Names.MAX_NAME + " characters.");
        }
        return clean;
    }

    public static void checkDescription(String description) {
        if (description != null && description.length() > Names.MAX_DESCRIPTION) {
            throw new BadRequestException("Descriptions are at most " + Names.MAX_DESCRIPTION + " characters.");
        }
    }

    public static String checkFlowName(String name) {
        String clean = Names.clean(name);
        if (clean.isEmpty()) {
            throw new BadRequestException("A flow needs a name.");
        }
        if (clean.length() > Names.MAX_NAME) {
            throw new BadRequestException("Flow names are at most " + Names.MAX_NAME + " characters.");
        }
        return clean;
    }

    public static ConflictException selfConnection() {
        return new ConflictException(
                "A connection must go to a different microservice.", ConflictException.SELF_CONNECTION);
    }
}
