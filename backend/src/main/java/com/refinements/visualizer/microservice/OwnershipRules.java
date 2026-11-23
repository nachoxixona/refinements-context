package com.refinements.visualizer.microservice;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Published ownership limits (spec 004 data-model.md §2 Microservice.team, .repositoryUrl, .docsUrl), shared with
 * the connections document reader so both validate the same way.
 */
public final class OwnershipRules {

    public static final int MAX_TEAM = 120;
    public static final int MAX_URL = 2048;

    private OwnershipRules() {}

    /** An absolute http(s) URL with a host, at most {@link #MAX_URL} characters. */
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
