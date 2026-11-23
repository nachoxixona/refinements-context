package com.refinements.visualizer.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.shared.errors.ParseError;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec card rules with no Spring and no database (data-model.md §1). */
class SpecCardDomainTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-23T11:00:00Z");

    private static SpecCard parsed(String title) {
        SpecCard c = SpecCard.create(UUID.randomUUID(), SourceType.PASTE, null, T0);
        c.applyParsed(title, "1.0", SpecFormat.OPENAPI_3_0, "raw", "hash-1", List.of(), T0);
        return c;
    }

    @Test
    void blankTitlesBecomeUntitledAndLongOnesAreTruncated() {
        assertThat(parsed("  ").getTitle()).isEqualTo("Untitled API");
        assertThat(parsed("x".repeat(250)).getTitle()).hasSize(SpecCard.MAX_TITLE);
    }

    @Test
    void aFailedSyncKeepsTheLastGoodContent() {
        SpecCard c = parsed("Orders");
        c.markFailed(List.of(new ParseError("paths", "broken")), T1);
        assertThat(c.getLastSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(c.getLastSyncErrors()).hasSize(1);
        assertThat(c.getContentHash()).isEqualTo("hash-1");
        assertThat(c.getRawContent()).isEqualTo("raw");

        c.markSynced(T1);
        assertThat(c.getLastSyncStatus()).isEqualTo(SyncStatus.OK);
        assertThat(c.getLastSyncErrors()).isEmpty();
    }

    @Test
    void aResyncIsUnchangedOnlyForTheSameHashAfterASuccessfulSync() {
        SpecCard c = parsed("Orders");
        assertThat(c.isUnchangedBy("hash-1")).isTrue();
        assertThat(c.isUnchangedBy("hash-2")).isFalse();
        c.markFailed(List.of(), T1);
        assertThat(c.isUnchangedBy("hash-1")).isFalse();
    }
}
