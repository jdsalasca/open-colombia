package top.eridu.opencolombia.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreshnessTest {
    private static Series series(LocalDate... dates) {
        var pts = java.util.Arrays.stream(dates).map(d -> new Point(d, 1.0)).toList();
        return new Series("s", "economia", "S", "%", "t", "u", "diaria", pts, Instant.EPOCH);
    }

    @Test
    void recentDataIsNotStale() {
        var f = Freshness.of(series(LocalDate.of(2026, 10, 11)), 5, LocalDate.of(2026, 10, 14));
        assertThat(f.lastDataDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(f.ageDays()).isEqualTo(3);
        assertThat(f.stale()).isFalse();
        assertThat(f.staleAfterDays()).isEqualTo(5);
    }

    @Test
    void olderThanThresholdIsStale() {
        var f = Freshness.of(series(LocalDate.of(2026, 10, 8)), 5, LocalDate.of(2026, 10, 14));
        assertThat(f.ageDays()).isEqualTo(6);
        assertThat(f.stale()).isTrue();
    }

    @Test
    void usesTheLatestPointEvenIfUnordered() {
        var f = Freshness.of(series(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 9, 1)), 5, LocalDate.of(2026, 10, 14));
        assertThat(f.lastDataDate()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void emptySeriesHasNoDateAndIsStale() {
        var f = Freshness.of(new Series("s", "t", "S", "%", "t", "u", "diaria", List.of(), null), 5, LocalDate.of(2026, 10, 14));
        assertThat(f.lastDataDate()).isNull();
        assertThat(f.stale()).isTrue();
    }

    @Test
    void seriesCarriesFreshnessAndKeepsItAcrossWindowing() {
        var s = series(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 10));
        var f = Freshness.of(s, 5, LocalDate.of(2026, 10, 14));
        assertThat(s.withFreshness(f).withPoints(List.of()).freshness()).isEqualTo(f);
    }
}
