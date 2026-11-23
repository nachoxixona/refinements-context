package com.refinements.visualizer.card.application.port.out;

import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecFormat;
import java.net.URI;
import java.util.List;

/** Deterministic OpenAPI 2.0/3.x parsing into endpoints and fields (constitution Principle I). */
public interface SpecParserPort {

    record ParsedSpecification(String title, String version, SpecFormat format, List<ParsedEndpoint> endpoints) {}

    /**
     * Parses and flattens {@code content}; {@code base} resolves relative {@code $ref}s; endpoint keys start with
     * {@code serviceKey}. Throws {@code SpecInvalidException} with locations when the spec is invalid.
     */
    ParsedSpecification parse(String content, URI base, String serviceKey);
}
