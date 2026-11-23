package com.refinements.visualizer.card.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.refinements.visualizer.card.SourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.net.URI;

/** {@code POST /api/cards}. PASTE needs {@code content}; URL needs an absolute http(s) {@code url}. */
@Schema(name = "ImportCardRequest")
public record ImportCardRequest(@NotNull SourceType sourceType, String content, String url) {

    @JsonIgnore
    @AssertTrue(message = "content is required when sourceType is PASTE")
    public boolean isContentPresentForPaste() {
        return sourceType != SourceType.PASTE || (content != null && !content.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "url must be an absolute http or https URL when sourceType is URL")
    public boolean isUrlValidForUrl() {
        if (sourceType != SourceType.URL) {
            return true;
        }
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
