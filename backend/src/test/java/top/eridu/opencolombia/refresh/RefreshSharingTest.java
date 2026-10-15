package top.eridu.opencolombia.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.IndicatorSpec.RefreshClass;
import top.eridu.opencolombia.source.SourceConnector;
import top.eridu.opencolombia.source.SourceException;

class RefreshSharingTest {
    @TempDir Path dir;

    private static IndicatorSpec spec(String id, String periodicity, RefreshClass rc, int stale) {
        return new IndicatorSpec(id, Theme.ECONOMIA, id, "%", periodicity, IndicatorSpec.Kind.WORLD_BANK, id, Map.of(),
                null, null, id, "", null, rc, stale);
    }

    private static Series series(String id, LocalDate last) {
        return new Series(id, "economia", id, "%", "t", "u", "diaria", List.of(new Point(last, 1)), Instant.EPOCH);
    }

    @Test
    void concurrentRefreshesShareASingleUpstreamRun() throws Exception {
        var calls = new AtomicInteger();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SourceConnector slow = new SourceConnector() {
            public boolean supports(IndicatorSpec s) { return true; }

            public Series fetch(IndicatorSpec s) throws SourceException {
                calls.incrementAndGet();
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new SourceException("interrumpido");
                }
                return series(s.id(), LocalDate.of(2026, 10, 13));
            }
        };
        var svc = new RefreshService(List.of(slow), new SeriesStore(dir),
                List.of(spec("a", "diaria", RefreshClass.DAILY, 7), spec("b", "diaria", RefreshClass.DAILY, 7)));
        ExecutorService ex = Executors.newFixedThreadPool(2);
        Future<RefreshReport> f1 = ex.submit(svc::refreshAll);
        started.await(5, TimeUnit.SECONDS);
        Future<RefreshReport> f2 = ex.submit(svc::refreshAll); // llega con la primera en curso
        Thread.sleep(150);
        release.countDown();
        var r1 = f1.get(10, TimeUnit.SECONDS);
        var r2 = f2.get(10, TimeUnit.SECONDS);
        ex.shutdownNow();
        assertThat(calls.get()).isEqualTo(2); // 2 indicadores, una sola ejecución (no 4)
        assertThat(r2).isSameAs(r1);
    }

    @Test
    void refreshClassOnlyTouchesThatClass() {
        var seen = new java.util.concurrent.CopyOnWriteArrayList<String>();
        SourceConnector rec = new SourceConnector() {
            public boolean supports(IndicatorSpec s) { return true; }

            public Series fetch(IndicatorSpec s) {
                seen.add(s.id());
                return series(s.id(), LocalDate.of(2026, 10, 13));
            }
        };
        var svc = new RefreshService(List.of(rec), new SeriesStore(dir),
                List.of(spec("diario", "diaria", RefreshClass.DAILY, 7), spec("lento", "anual", RefreshClass.SLOW, 800)));
        svc.refreshClass(RefreshClass.DAILY);
        assertThat(seen).containsExactly("diario");
    }

    @Test
    void anyStaleIsTrueForMissingOrOldSeriesAndFalseWhenAllAreFresh() {
        var store = new SeriesStore(dir);
        var specs = List.of(spec("a", "diaria", RefreshClass.DAILY, 7), spec("b", "diaria", RefreshClass.DAILY, 7));
        var svc = new RefreshService(List.of(), store, specs);
        var today = LocalDate.of(2026, 10, 14);
        assertThat(svc.anyStale(today)).isTrue(); // nada cargado
        store.put(series("a", LocalDate.of(2026, 10, 13)));
        assertThat(svc.anyStale(today)).isTrue(); // falta b
        store.put(series("b", LocalDate.of(2026, 10, 1))); // 13 días > 7
        assertThat(svc.anyStale(today)).isTrue();
        store.put(series("b", LocalDate.of(2026, 10, 12)));
        assertThat(svc.anyStale(today)).isFalse();
    }

    @Test
    void errorMessagesNeverLeakExceptionClassNamesOrPaths() {
        SourceConnector boom = new SourceConnector() {
            public boolean supports(IndicatorSpec s) { return true; }

            public Series fetch(IndicatorSpec s) {
                throw new IllegalStateException("java.io.IOException: /data/secret/path boom");
            }
        };
        var svc = new RefreshService(List.of(boom), new SeriesStore(dir),
                List.of(spec("a", "diaria", RefreshClass.DAILY, 7)));
        var r = svc.refreshAll().results().get(0);
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).doesNotContain("java.").doesNotContain("/data").doesNotContain("Exception");
    }
}
