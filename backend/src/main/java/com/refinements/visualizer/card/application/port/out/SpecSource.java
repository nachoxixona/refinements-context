package com.refinements.visualizer.card.application.port.out;

import java.net.URI;

/** Fetches spec text from a URL (with the SSRF and size limits of the constitution's security baseline). */
public interface SpecSource {

    /** Checks and normalizes a user-given spec URL; 400 when it is not an absolute http(s) URL. */
    URI validate(String url);

    String fetch(URI url);
}
