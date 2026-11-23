package com.refinements.visualizer.card.adapter.out.persistence;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.shared.errors.ParseError;
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

/** Row of the {@code spec_card} table (V1__schema.sql). */
@Entity
@Table(name = "spec_card")
class SpecCardEntity {

    @Id
    UUID id;

    @Column(name = "title", nullable = false, length = 200)
    String title;

    @Column(name = "api_version", length = 50)
    String apiVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_format", nullable = false, length = 20)
    SpecFormat specFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    SourceType sourceType;

    @Column(name = "source_url", length = 2048)
    String sourceUrl;

    /** The last successfully parsed raw spec (max 10 MB). */
    @Column(name = "raw_content", nullable = false)
    String rawContent;

    @Column(name = "content_hash", nullable = false, length = 64)
    String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoints", nullable = false)
    List<ParsedEndpoint> endpoints = new ArrayList<>();

    @Column(name = "last_sync_at", nullable = false)
    Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status", nullable = false, length = 10)
    SyncStatus lastSyncStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_sync_errors", nullable = false)
    List<ParseError> lastSyncErrors = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected SpecCardEntity() {}

    SpecCardEntity(UUID id) {
        this.id = id;
    }
}
