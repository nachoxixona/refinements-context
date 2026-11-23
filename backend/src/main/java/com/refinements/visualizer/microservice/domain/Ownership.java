package com.refinements.visualizer.microservice.domain;

import com.refinements.visualizer.microservice.OwnershipRules;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;

/** Ownership rules (spec 004 data-model.md §2 Microservice.team, Microservice.repositoryUrl, Microservice.docsUrl). */
public final class Ownership {

    public static final int MAX_TEAM = OwnershipRules.MAX_TEAM;
    public static final int MAX_URL = OwnershipRules.MAX_URL;

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
        return OwnershipRules.isValidUrl(url);
    }
}
