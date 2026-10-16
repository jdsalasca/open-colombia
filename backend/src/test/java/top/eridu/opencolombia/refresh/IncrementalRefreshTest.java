package top.eridu.opencolombia.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.SourceConnector;
import top.eridu.opencolombia.source.SourceException;

/** Las series con ventana móvil (IDEAM) piden solo lo nuevo cuando ya hay caché reciente. */
class IncrementalRefreshTest {
    @TempDir Path dir;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

    private static IndicatorSpec rolling() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", "date_trunc_ymd(f) as dia, avg(v) as v");
        p.put("sinceDaysAgo", "120");
        p.put("sinceField", "f");
        p.put("chunkDays", "30");
        return new IndicatorSpec("temp", Theme.AMBIENTE_ENERGIA, "Temp", "C", "diaria", IndicatorSpec.Kind.SODA,
                "sbwg-7ju4", p, "v", "dia");
    }

    private static List<Point> days(LocalDate from, int n, double base) {
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Point(from.plusDays(i), base + i));
        return out;
    }

    private static Series series(List<Point> pts) {
        return new Series("temp", "ambiente-energia", "Temp", "C", "t", "u", "diaria", pts, Instant.EPOCH);
    }

    /** Caché producida por esta misma definición del indicador, consultada ayer. */
    private static Series cachedBy(IndicatorSpec spec, List<Point> pts) {
        return new Series("temp", "ambiente-energia", "Temp", "C", "t", "u", "diaria", pts,
                Instant.parse("2026-10-14T10:00:00Z")).withFingerprint(spec.fingerprint());
    }

    /** Conector que registra con qué ventana lo llamaron y devuelve los puntos indicados. */
    private static SourceConnector recorder(List<String> seenWindows, List<Point> returns) {
        return new SourceConnector() {
            public boolean supports(IndicatorSpec s) { return true; }

            public Series fetch(IndicatorSpec s) throws SourceException {
                seenWindows.add(s.params().get("sinceDaysAgo"));
                return series(returns);
            }
        };
    }

    @Test
    void withARecentCacheOnlyTheMissingDaysPlusMarginAreRequested() {
        var store = new SeriesStore(dir);
        store.put(cachedBy(rolling(), days(TODAY.minusDays(100), 98, 10))); // último dato: hoy - 3
        var seen = new CopyOnWriteArrayList<String>();
        var fresh = days(TODAY.minusDays(12), 12, 500);
        var svc = new RefreshService(List.of(recorder(seen, fresh)), store, List.of(rolling()), CLOCK);
        svc.refreshAll();
        assertThat(seen).containsExactly("13"); // 3 días de edad + 10 de margen
    }

    @Test
    void theMergedSeriesKeepsOldPointsAndOverwritesTheRefetchedOnes() {
        var store = new SeriesStore(dir);
        store.put(cachedBy(rolling(), days(TODAY.minusDays(100), 98, 10)));
        var fresh = days(TODAY.minusDays(12), 12, 500);
        new RefreshService(List.of(recorder(new CopyOnWriteArrayList<>(), fresh)), store, List.of(rolling()), CLOCK)
                .refreshAll();
        var merged = store.get("temp").orElseThrow().points();
        assertThat(merged.get(0).date()).isEqualTo(TODAY.minusDays(100)); // lo viejo se conserva
        assertThat(merged.stream().filter(p -> p.date().equals(TODAY.minusDays(12))).findFirst().orElseThrow().value())
                .isEqualTo(500.0); // lo re-pedido pisa lo guardado
        assertThat(merged).extracting(Point::date).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void pointsOlderThanTheOriginalWindowAreTrimmed() {
        var store = new SeriesStore(dir);
        store.put(cachedBy(rolling(), days(TODAY.minusDays(200), 198, 10))); // 200 días de historia guardada
        new RefreshService(List.of(recorder(new CopyOnWriteArrayList<>(), days(TODAY.minusDays(12), 12, 1))), store,
                List.of(rolling()), CLOCK).refreshAll();
        var first = store.get("temp").orElseThrow().points().get(0).date();
        assertThat(first).isAfterOrEqualTo(TODAY.minusDays(120));
    }

    @Test
    void aCacheProducedByAnOlderDefinitionOfTheIndicatorIsRefetchedInFull() {
        // regresión: tras cambiar una regla (p. ej. descartar días atípicos) el incremental dejaba vivos los
        // puntos viejos malos dentro de la ventana de 120 días
        var store = new SeriesStore(dir);
        store.put(series(days(TODAY.minusDays(100), 98, 10)).withFingerprint("definicion-vieja"));
        var seen = new CopyOnWriteArrayList<String>();
        new RefreshService(List.of(recorder(seen, days(TODAY.minusDays(12), 12, 500))), store, List.of(rolling()), CLOCK)
                .refreshAll();
        assertThat(seen).containsExactly("120");
    }

    @Test
    void aLegacyCacheWithoutFingerprintIsRefetchedInFull() {
        var store = new SeriesStore(dir);
        store.put(new Series("temp", "ambiente-energia", "Temp", "C", "t", "u", "diaria",
                days(TODAY.minusDays(100), 98, 10), Instant.parse("2026-10-14T10:00:00Z"))); // sin huella
        var seen = new CopyOnWriteArrayList<String>();
        new RefreshService(List.of(recorder(seen, days(TODAY.minusDays(12), 12, 500))), store, List.of(rolling()), CLOCK)
                .refreshAll();
        assertThat(seen).containsExactly("120");
    }

    @Test
    void aWeekOldCacheIsRefetchedInFullSoBackfillsAndCorrectionsLand() {
        var store = new SeriesStore(dir);
        var old = new Series("temp", "ambiente-energia", "Temp", "C", "t", "u", "diaria",
                days(TODAY.minusDays(100), 98, 10), Instant.parse("2026-10-05T10:00:00Z")) // 9 días
                .withFingerprint(rolling().fingerprint());
        store.put(old);
        var seen = new CopyOnWriteArrayList<String>();
        new RefreshService(List.of(recorder(seen, days(TODAY.minusDays(12), 12, 500))), store, List.of(rolling()), CLOCK)
                .refreshAll();
        assertThat(seen).containsExactly("120");
    }

    @Test
    void theStoredSeriesCarriesTheFingerprintOfTheDefinitionThatProducedIt() {
        var store = new SeriesStore(dir);
        new RefreshService(List.of(recorder(new CopyOnWriteArrayList<>(), days(TODAY.minusDays(5), 5, 1))), store,
                List.of(rolling()), CLOCK).refreshAll();
        assertThat(store.get("temp").orElseThrow().fingerprint()).isEqualTo(rolling().fingerprint());
    }

    @Test
    void fingerprintChangesWithTheRulesButNotWithParamOrder() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("x", "1");
        a.put("y", "2");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("y", "2");
        b.put("x", "1");
        var s1 = new IndicatorSpec("t", Theme.AMBIENTE_ENERGIA, "T", "C", "diaria", IndicatorSpec.Kind.SODA, "r", a, "v", "d");
        var s2 = new IndicatorSpec("t", Theme.AMBIENTE_ENERGIA, "T", "C", "diaria", IndicatorSpec.Kind.SODA, "r", b, "v", "d");
        assertThat(s1.fingerprint()).isEqualTo(s2.fingerprint());
        assertThat(s1.withParam("dropOutlierCounts", "true").fingerprint()).isNotEqualTo(s1.fingerprint());
    }

    @Test
    void emptyCacheRequestsTheWholeWindow() {
        var seen = new CopyOnWriteArrayList<String>();
        new RefreshService(List.of(recorder(seen, days(TODAY.minusDays(5), 5, 1))), new SeriesStore(dir),
                List.of(rolling()), CLOCK).refreshAll();
        assertThat(seen).containsExactly("120");
    }

    @Test
    void aCacheOlderThanTheWindowIsRefetchedInFull() {
        var store = new SeriesStore(dir);
        store.put(cachedBy(rolling(), days(TODAY.minusDays(300), 100, 10))); // último dato: hace 201 días
        var seen = new CopyOnWriteArrayList<String>();
        new RefreshService(List.of(recorder(seen, days(TODAY.minusDays(5), 5, 1))), store, List.of(rolling()), CLOCK)
                .refreshAll();
        assertThat(seen).containsExactly("120");
    }

    @Test
    void specsWithoutARollingWindowAreNeverIncremental() {
        var store = new SeriesStore(dir);
        store.put(new Series("pib", "economia", "PIB", "%", "t", "u", "anual",
                List.of(new Point(LocalDate.of(2024, 1, 1), 1)), Instant.EPOCH));
        var seen = new CopyOnWriteArrayList<String>();
        var wb = new IndicatorSpec("pib", Theme.ECONOMIA, "PIB", "%", "anual", IndicatorSpec.Kind.WORLD_BANK, "X",
                Map.of(), null, null);
        new RefreshService(List.of(recorder(seen, List.of(new Point(LocalDate.of(2025, 1, 1), 2)))), store, List.of(wb),
                CLOCK).refreshAll();
        assertThat(seen).containsExactly((String) null); // sin sinceDaysAgo
        assertThat(store.get("pib").orElseThrow().points()).hasSize(1); // se reemplaza completa, como siempre
    }
}
