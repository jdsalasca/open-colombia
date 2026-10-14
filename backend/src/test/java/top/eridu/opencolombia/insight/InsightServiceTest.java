package top.eridu.opencolombia.insight;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Insight;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

class InsightServiceTest {
    @TempDir Path dir;

    private static Series series(String id, int n, double base, double step) {
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) pts.add(new Point(LocalDate.of(2010 + i, 1, 1), base + step * i));
        return new Series(id, "x", id, "%", "t", "u", "anual", pts, Instant.EPOCH);
    }

    @Test
    void emptyStoreGivesNoInsights() {
        assertThat(new InsightService(new SeriesStore(dir)).insights()).isEmpty();
    }

    @Test
    void relationReportsComputedSlopeR2AndN() {
        var store = new SeriesStore(dir);
        store.put(series("empleo-vulnerable", 10, 40, -0.5));
        store.put(series("pobreza", 10, 30, -1.0));
        var ins = new InsightService(store).insights();
        Insight i = ins.stream().filter(x -> x.themeId().equals("empleo")).filter(x -> x.finding().contains("pobreza"))
                .findFirst().orElseThrow();
        assertThat(i.confidence().n()).isEqualTo(10);
        assertThat(i.confidence().r2()).isGreaterThan(0.99);
        assertThat(i.confidence().level()).isEqualTo("media");
        assertThat(i.finding()).contains("n = 10").contains("R²");
        assertThat(i.finding()).containsIgnoringCase("no implica causalidad");
    }

    @Test
    void tooFewPointsIsMarkedInsufficient() {
        var store = new SeriesStore(dir);
        store.put(series("empleo-vulnerable", 3, 40, -0.5));
        store.put(series("pobreza", 3, 30, -1.0));
        Insight i = new InsightService(store).insights().stream()
                .filter(x -> x.themeId().equals("empleo")).filter(x -> x.finding().contains("pobreza"))
                .findFirst().orElseThrow();
        assertThat(i.confidence().level()).isEqualTo("insuficiente");
        assertThat(i.proposal()).containsIgnoringCase("no hay evidencia suficiente");
    }

    @Test
    void trendInsightIncludesForecast() {
        var store = new SeriesStore(dir);
        store.put(series("desempleo", 12, 12, -0.3));
        var ins = new InsightService(store).insights();
        assertThat(ins).anySatisfy(x -> {
            assertThat(x.themeId()).isEqualTo("empleo");
            assertThat(x.finding()).contains("Tendencia").contains("proyección");
        });
    }
}
