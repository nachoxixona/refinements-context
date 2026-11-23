package com.refinements.visualizer.common;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed binding of the {@code visualizer.*} configuration keys (T008). */
@ConfigurationProperties("visualizer")
public record VisualizerProperties(
        @DefaultValue Spec spec,
        @DefaultValue ConnectionsDocument connectionsDocument,
        @DefaultValue Http http,
        @DefaultValue Fetch fetch,
        @DefaultValue Refs refs) {

    public record Spec(@DefaultValue("10485760") long maxBytes) {}

    /** Connections document limits (research R4). */
    public record ConnectionsDocument(
            @DefaultValue("2097152") long maxBytes,
            @DefaultValue("5000") int maxConnections,
            @DefaultValue("2000") int maxMicroservices) {}

    public record Http(@DefaultValue("12582912") long maxBodyBytes) {}

    public record Fetch(
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("15s") Duration readTimeout,
            @DefaultValue("3") int maxRedirects,
            @DefaultValue List<String> allowedHosts) {}

    public record Refs(@DefaultValue List<String> remoteAllowlist) {}
}
