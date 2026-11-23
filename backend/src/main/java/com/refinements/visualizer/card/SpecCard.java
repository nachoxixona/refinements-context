package com.refinements.visualizer.card;

import com.refinements.visualizer.common.errors.ParseError;
import com.refinements.visualizer.ingestion.model.ParsedEndpoint;
import com.refinements.visualizer.ingestion.model.SpecFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A parsed API spec shown as a card (data-model.md §1). Never creates connections (FR-007). */
@Entity
@Table(name = "spec_card")
public class SpecCard {

    public static final int MAX_TITLE = 200;

    @Id
    private UUID id;

    @Column(name = "title", nullable = false, length = MAX_TITLE)
    private String title;

    @Column(name = "api_version", length = 50)
    private String apiVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_format", nullable = false, length = 20)
    private SpecFormat specFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private SourceType sourceType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    /** The last successfully parsed raw spec (max 10 MB). */
    @Column(name = "raw_content", nullable = false)
    private String rawContent;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoints", nullable = false)
    private List<ParsedEndpoint> endpoints = new ArrayList<>();

    @Column(name = "last_sync_at", nullable = false)
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status", nullable = false, length = 10)
    private SyncStatus lastSyncStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_sync_errors", nullable = false)
    private List<ParseError> lastSyncErrors = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpecCard() {}

    public SpecCard(SourceType sourceType, String sourceUrl, Instant now) {
        this.id = UUID.randomUUID();
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSyncAt = now;
        this.lastSyncStatus = SyncStatus.OK;
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
        this.apiVersion = truncate(apiVersion, 50);
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
        return endpoints;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public SyncStatus getLastSyncStatus() {
        return lastSyncStatus;
    }

    public List<ParseError> getLastSyncErrors() {
        return lastSyncErrors;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
