package top.eridu.opencolombia.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.SourceConnector;
import top.eridu.opencolombia.source.SourceException;

class RefreshServiceTest {
    @TempDir Path dir;

    private static IndicatorSpec spec(String id) {
        return new IndicatorSpec(id, Theme.ECONOMIA, id, "%", "anual", IndicatorSpec.Kind.WORLD_BANK, id, Map.of(),
                null, null);
    }

    private static Series series(String id, double v) {
        return new Series(id, "economia", id, "%", "t", "u", "anual",
                List.of(new Point(LocalDate.of(2024, 1, 1), v)), Instant.EPOCH);
    }

    /** Conector falso: cada id decide qué hacer en cada intento. */
    private static SourceConnector connector(java.util.function.BiFunction<String, Integer, Series> f) {
        var calls = new java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>();
        return new SourceConnector() {
            public boolean supports(IndicatorSpec s) { return true; }

            public Series fetch(IndicatorSpec s) throws SourceException {
                int n = calls.computeIfAbsent(s.id(), k -> new AtomicInteger()).incrementAndGet();
                Series r = f.apply(s.id(), n);
                if (r == null) throw new SourceException("boom " + s.id());
                return r;
            }
        };
    }

    @Test
    void allOkStoresEverything() {
        var store = new SeriesStore(dir);
        var svc = new RefreshService(List.of(connector((id, n) -> series(id, 1))), store,
                List.of(spec("a"), spec("b")));
        var report = svc.refreshAll();
        assertThat(report.results()).allMatch(SourceResult::ok);
        assertThat(store.all()).extracting(Series::id).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void failureKeepsPreviousSeriesAndOthersProceed() {
        var store = new SeriesStore(dir);
        store.put(series("a", 99));
        var svc = new RefreshService(List.of(connector((id, n) -> id.equals("a") ? null : series(id, 2))), store,
                List.of(spec("a"), spec("b")));
        var report = svc.refreshAll();
        assertThat(report.results()).filteredOn(r -> r.id().equals("a")).singleElement()
                .satisfies(r -> assertThat(r.ok()).isFalse());
        assertThat(store.get("a").orElseThrow().points().get(0).value()).isEqualTo(99);
        assertThat(store.get("b")).isPresent();
    }

    @Test
    void transientFailureRecoversOnRetry() {
        var store = new SeriesStore(dir);
        var svc = new RefreshService(List.of(connector((id, n) -> n == 1 ? null : series(id, 3))), store,
                List.of(spec("a")));
        assertThat(svc.refreshAll().results()).singleElement().satisfies(r -> assertThat(r.ok()).isTrue());
    }

    @Test
    void emptyResultDoesNotOverwriteExistingData() {
        var store = new SeriesStore(dir);
        store.put(series("a", 5));
        var empty = series("a", 0).withPoints(List.of());
        var svc = new RefreshService(List.of(connector((id, n) -> empty)), store, List.of(spec("a")));
        assertThat(svc.refreshAll().results()).singleElement().satisfies(r -> assertThat(r.ok()).isFalse());
        assertThat(store.get("a").orElseThrow().points()).hasSize(1);
    }
}
