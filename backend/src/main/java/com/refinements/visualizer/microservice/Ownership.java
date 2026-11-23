package com.refinements.visualizer.microservice;

import com.refinements.visualizer.common.errors.BadRequestException;
import com.refinements.visualizer.connection.Names;
import java.net.URI;
import java.net.URISyntaxException;

/** Ownership rules (spec 004 data-model.md §2 Microservice.team, Microservice.repositoryUrl, Microservice.docsUrl). */
public final class Ownership {

    public static final int MAX_TEAM = 120;
    public static final int MAX_URL = 2048;

    private Ownership() {}

    /** Cleaned team, or null when blank; 400 when too long. */
    public static String team(String team) {
        if (team == null) {
            return null;
        }
        String clean = Names.clean(team);
        if (clean.isEmpty()) {
            return null;
        }
        if (clean.length() > MAX_TEAM) {
            throw new BadRequestException("Team names are at most " + MAX_TEAM + " characters.");
        }
        return clean;
    }

    /** Trimmed URL, null when blank; 400 when it is not an absolute http(s) URL with a host. */
    public static String url(String url, String label) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (!isValidUrl(trimmed)) {
            throw new BadRequestException(label + " must be an http or https URL, at most " + MAX_URL + " characters.");
        }
        return trimmed;
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.length() > MAX_URL) {
            return false;
        }
        try {
            URI u = new URI(url);
            String scheme = u.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && u.getHost() != null
                    && !u.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
