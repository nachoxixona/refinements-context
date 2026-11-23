package com.refinements.visualizer.layout.adapter.out.persistence;

import com.refinements.visualizer.shared.XYPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The single row of {@code baseline_layout} (V1__schema.sql); {@code version} is the optimistic lock. */
@Entity
@Table(name = "baseline_layout")
class BaselineLayoutEntity {

    static final short SINGLETON_ID = 1;

    @Id
    Short id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positions", nullable = false)
    Map<String, XYPosition> positions = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "collapsed_cards", nullable = false)
    List<UUID> collapsedCards = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    int version;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected BaselineLayoutEntity() {}
}
