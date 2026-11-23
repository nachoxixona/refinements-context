package com.refinements.visualizer.card.adapter.out.parser;

import com.refinements.visualizer.card.model.SpecFormat;
import io.swagger.v3.oas.models.OpenAPI;

/** A fully resolved OpenAPI 3 model plus the format the original document was written in. */
public record ParsedSpec(OpenAPI openApi, SpecFormat format) {

    public String title() {
        return openApi.getInfo() == null ? null : openApi.getInfo().getTitle();
    }

    public String version() {
        return openApi.getInfo() == null ? null : openApi.getInfo().getVersion();
    }
}
