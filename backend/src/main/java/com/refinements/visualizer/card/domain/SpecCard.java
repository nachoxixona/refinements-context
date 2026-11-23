package com.refinements.visualizer.card.domain;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.shared.errors.ParseError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A parsed API spec shown as a card (data-model.md §1). Never creates connections (FR-007). Plain Java. */
public class SpecCard {

    public static final int MAX_TITLE = 200;
    static final int MAX_API_VERSION = 50;

    private final UUID id;
    private String title;
    private String apiVersion;
    private SpecFormat specFormat;
    private final SourceType sourceType;
    private final String sourceUrl;
    private String rawContent;
    private String contentHash;
    private List<ParsedEndpoint> endpoints;
    private Instant lastSyncAt;
    private SyncStatus lastSyncStatus;
    private List<ParseError> lastSyncErrors;
    private final Instant createdAt;
    private Instant updatedAt;

    /** A new card with the given id (the id is the parser's service key, so it is chosen before parsing). */
    public static SpecCard create(UUID id, SourceType sourceType, String sourceUrl, Instant now) {
        return new SpecCard(
                id,
                null,
                null,
                null,
                sourceType,
                sourceUrl,
                null,
                null,
                List.of(),
                now,
                SyncStatus.OK,
                List.of(),
                now,
                now);
    }

    /** Rebuilds a stored card (persistence adapter only). */
    public static SpecCard restore(
            UUID id,
            String title,
            String apiVersion,
            SpecFormat specFormat,
            SourceType sourceType,
            String sourceUrl,
            String rawContent,
            String contentHash,
            List<ParsedEndpoint> endpoints,
            Instant lastSyncAt,
            SyncStatus lastSyncStatus,
            List<ParseError> lastSyncErrors,
            Instant createdAt,
            Instant updatedAt) {
        return new SpecCard(
                id,
                title,
                apiVersion,
                specFormat,
                sourceType,
                sourceUrl,
                rawContent,
                contentHash,
                endpoints,
                lastSyncAt,
                lastSyncStatus,
                lastSyncErrors,
                createdAt,
                updatedAt);
    }

    private SpecCard(
            UUID id,
            String title,
            String apiVersion,
            SpecFormat specFormat,
            SourceType sourceType,
            String sourceUrl,
            String rawContent,
            String contentHash,
            List<ParsedEndpoint> endpoints,
            Instant lastSyncAt,
            SyncStatus lastSyncStatus,
            List<ParseError> lastSyncErrors,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.apiVersion = apiVersion;
        this.specFormat = specFormat;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.rawContent = rawContent;
        this.contentHash = contentHash;
        this.endpoints = new ArrayList<>(endpoints);
        this.lastSyncAt = lastSyncAt;
        this.lastSyncStatus = lastSyncStatus;
        this.lastSyncErrors = new ArrayList<>(lastSyncErrors);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Stores a successfully parsed version and clears any previous sync error. */
    public void applyParsed(
            String title,
            String apiVersion,
            SpecFormat specFormat,
            String rawContent,
            String contentHash,
            List<ParsedEndpoint> endpoints,
            Instant now) {
        this.title = truncate(title == null || title.isBlank() ? "Untitled API" : title.trim(), MAX_TITLE);
        this.apiVersion = truncate(apiVersion, MAX_API_VERSION);
        this.specFormat = specFormat;
        this.rawContent = rawContent;
        this.contentHash = contentHash;
        this.endpoints = new ArrayList<>(endpoints);
        markSynced(now);
    }

    public void markSynced(Instant now) {
        this.lastSyncStatus = SyncStatus.OK;
        this.lastSyncErrors = new ArrayList<>();
        this.lastSyncAt = now;
        this.updatedAt = now;
    }

    /** A failed re-sync keeps the last good content and endpoints (data-model.md §1). */
    public void markFailed(List<ParseError> errors, Instant now) {
        this.lastSyncStatus = SyncStatus.FAILED;
        this.lastSyncErrors = new ArrayList<>(errors);
        this.lastSyncAt = now;
        this.updatedAt = now;
    }

    /** True when {@code hash} is this card's content and the last sync succeeded (a no-op re-sync). */
    public boolean isUnchangedBy(String hash) {
        return hash.equals(contentHash) && lastSyncStatus == SyncStatus.OK;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public SpecFormat getSpecFormat() {
        return specFormat;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getRawContent() {
        return rawContent;
    }

    public String getContentHash() {
        return contentHash;
    }

    public List<ParsedEndpoint> getEndpoints() {
        return Collections.unmodifiableList(endpoints);
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public SyncStatus getLastSyncStatus() {
        return lastSyncStatus;
    }

    public List<ParseError> getLastSyncErrors() {
        return Collections.unmodifiableList(lastSyncErrors);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
