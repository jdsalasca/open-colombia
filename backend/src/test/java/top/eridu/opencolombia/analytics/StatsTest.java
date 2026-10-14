package top.eridu.opencolombia.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.Test;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

class StatsTest {
    /** Serie anual desde 2015 con n puntos y valor f(i). */
    private static Series annual(int n, IntToDoubleFunction f) {
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) pts.add(new Point(LocalDate.of(2015 + i, 1, 1), f.applyAsDouble(i)));
        return new Series("s", "economia", "S", "%", "t", "u", "anual", pts, Instant.EPOCH);
    }

    @Test
    void trendOfExactLine() {
        var r = Stats.trend(annual(6, i -> 2 * i + 1)).orElseThrow();
        assertThat(r.slope()).isCloseTo(2.0, within(0.02)); // ~2 por año; x en días/365.25 no es equidistante
        assertThat(r.r2()).isCloseTo(1.0, within(1e-4));
        assertThat(r.n()).isEqualTo(6);
    }

    @Test
    void tooFewPointsIsEmpty() {
        var s = annual(3, i -> i);
        assertThat(Stats.trend(s)).isEmpty();
        assertThat(Stats.correlation(s, s)).isEmpty();
        assertThat(Stats.forecast(s, 1)).isEmpty();
        assertThat(Stats.regress(s, s)).isEmpty();
    }

    @Test
    void constantSeriesHasNoNaN() {
        var r = Stats.trend(annual(6, i -> 5.0)).orElseThrow();
        assertThat(r.slope()).isCloseTo(0.0, within(1e-9));
        assertThat(r.r2()).isNotNaN();
        assertThat(Stats.correlation(annual(6, i -> 5.0), annual(6, i -> i))).isEmpty();
    }

    @Test
    void perfectlyInverseCorrelation() {
        assertThat(Stats.correlation(annual(8, i -> i), annual(8, i -> 10 - i)).orElseThrow())
                .isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void regressionYOnX() {
        var r = Stats.regress(annual(8, i -> i), annual(8, i -> 3 * i + 2)).orElseThrow();
        assertThat(r.slope()).isCloseTo(3.0, within(1e-9));
        assertThat(r.intercept()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void forecastOnExactLineHasTightInterval() {
        var f = Stats.forecast(annual(8, i -> 2 * i + 1), 1).orElseThrow();
        assertThat(f.value()).isCloseTo(2 * 8 + 1, within(0.05));
        assertThat(f.high() - f.low()).isLessThan(0.1);
    }

    @Test
    void yoyChangePercent() {
        var s = annual(3, i -> new double[] {100, 110, 121}[i]);
        assertThat(Stats.yoyChange(s).orElseThrow()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void yoyWithZeroBaseIsEmpty() {
        assertThat(Stats.yoyChange(annual(2, i -> i == 0 ? 0 : 5))).isEmpty();
    }
}
