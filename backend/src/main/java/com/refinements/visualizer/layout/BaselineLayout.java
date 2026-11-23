package com.refinements.visualizer.layout;

import com.refinements.visualizer.common.XYPosition;
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

/**
 * Single row (data-model.md §5): BaselineLayout.positions keyed {@code ms:{id}} / {@code card:{id}},
 * BaselineLayout.collapsedCards, and BaselineLayout.version as the optimistic lock.
 */
@Entity
@Table(name = "baseline_layout")
public class BaselineLayout {

    public static final short SINGLETON_ID = 1;
    public static final String MS_PREFIX = "ms:";
    public static final String CARD_PREFIX = "card:";

    @Id
    private Short id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positions", nullable = false)
    private Map<String, XYPosition> positions = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "collapsed_cards", nullable = false)
    private List<UUID> collapsedCards = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaselineLayout() {}

    public static String msKey(UUID id) {
        return MS_PREFIX + id;
    }

    public static String cardKey(UUID id) {
        return CARD_PREFIX + id;
    }

    public Short getId() {
        return id;
    }

    public Map<String, XYPosition> getPositions() {
        return positions;
    }

    public List<UUID> getCollapsedCards() {
        return collapsedCards;
    }

    public void replace(Map<String, XYPosition> positions, List<UUID> collapsedCards, Instant now) {
        this.positions = new LinkedHashMap<>(positions);
        this.collapsedCards = new ArrayList<>(collapsedCards);
        this.updatedAt = now;
    }

    public int getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
