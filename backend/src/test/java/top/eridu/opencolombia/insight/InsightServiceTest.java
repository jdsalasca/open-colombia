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
        assertThat(i.confidence().level()).isEqualTo("baja"); // solo comparten tendencia
        assertThat(i.finding()).contains("n = 10").contains("R²").contains("espuria");
        assertThat(i.finding()).containsIgnoringCase("no implica causalidad");
    }

    @Test
    void genuineComovementKeepsMediaLevel() {
        var store = new SeriesStore(dir);
        List<Point> xs = new ArrayList<>();
        List<Point> ys = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double x = 40 + i * -0.5 + Math.sin(i) * 3;
            xs.add(new Point(LocalDate.of(2010 + i, 1, 1), x));
            ys.add(new Point(LocalDate.of(2010 + i, 1, 1), 0.8 * x));
        }
        store.put(series("empleo-vulnerable", 1, 0, 0).withPoints(xs));
        store.put(series("pobreza", 1, 0, 0).withPoints(ys));
        Insight i = new InsightService(store).insights().stream()
                .filter(x -> x.themeId().equals("empleo")).filter(x -> x.finding().contains("pobreza"))
                .findFirst().orElseThrow();
        assertThat(i.confidence().level()).isEqualTo("media");
        assertThat(i.finding()).doesNotContain("espuria");
    }

    @Test
    void weakFitIsNotGivenMediaConfidence() {
        var store = new SeriesStore(dir);
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < 20; i++) pts.add(new Point(LocalDate.of(2005 + i, 1, 1), i % 2 == 0 ? 10 : 12));
        store.put(series("desempleo", 1, 0, 0).withPoints(pts));
        Insight i = new InsightService(store).insights().stream()
                .filter(x -> x.themeId().equals("empleo")).filter(x -> x.finding().contains("Tendencia"))
                .findFirst().orElseThrow();
        assertThat(i.confidence().r2()).isLessThan(0.3);
        assertThat(i.confidence().level()).isEqualTo("baja");
        assertThat(i.proposal()).containsIgnoringCase("evidencia débil");
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
