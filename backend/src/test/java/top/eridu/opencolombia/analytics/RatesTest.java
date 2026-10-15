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

class RatesTest {
    private static Series annual(int n, IntToDoubleFunction f) {
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) pts.add(new Point(LocalDate.of(1995 + i, 1, 1), f.applyAsDouble(i)));
        return new Series("s", "economia", "S", "%", "t", "u", "anual", pts, Instant.EPOCH);
    }

    private static Series monthly(int n, IntToDoubleFunction f) {
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) pts.add(new Point(LocalDate.of(2024, 1, 1).plusMonths(i), f.applyAsDouble(i)));
        return new Series("m", "economia", "M", "COP", "t", "u", "mensual", pts, Instant.EPOCH);
    }

    @Test
    void basicChangeMeanMinMax() {
        var r = Rates.compute(annual(3, i -> new double[] {100, 110, 121}[i])).orElseThrow();
        assertThat(r.n()).isEqualTo(3);
        assertThat(r.change()).isCloseTo(21.0, within(1e-9));
        assertThat(r.changePct()).isCloseTo(21.0, within(1e-9));
        assertThat(r.mean()).isCloseTo(110.3333, within(1e-3));
        assertThat(r.min().value()).isEqualTo(100.0);
        assertThat(r.max().value()).isEqualTo(121.0);
    }

    @Test
    void fewerThanTwoPointsIsEmpty() {
        assertThat(Rates.compute(annual(1, i -> 5))).isEmpty();
        assertThat(Rates.compute(annual(0, i -> 5))).isEmpty();
    }

    @Test
    void annualizedOnlyWhenSpanIsAboutAYearOrMore() {
        var twoYears = Rates.compute(monthly(25, i -> 100 * Math.pow(1.21, i / 24.0))).orElseThrow();
        assertThat(twoYears.annualizedPct()).isCloseTo(10.0, within(0.05));
        var sixMonths = Rates.compute(monthly(7, i -> 100 + i)).orElseThrow();
        assertThat(sixMonths.annualizedPct()).isNull();
        var oneYear = Rates.compute(monthly(13, i -> 100 + i)).orElseThrow(); // 12 meses de cambio
        assertThat(oneYear.annualizedPct()).isNotNull();
    }

    @Test
    void annualizedAndPctAreNullWhenInitialIsZeroOrNegative() {
        var r = Rates.compute(monthly(25, i -> i == 0 ? 0 : 10)).orElseThrow();
        assertThat(r.changePct()).isNull();
        assertThat(r.annualizedPct()).isNull();
        var neg = Rates.compute(monthly(25, i -> i == 0 ? -5 : 10)).orElseThrow();
        assertThat(neg.annualizedPct()).isNull();
    }

    @Test
    void constantSeriesHasZeroStdDev() {
        var r = Rates.compute(annual(5, i -> 7)).orElseThrow();
        assertThat(r.stdDev()).isEqualTo(0.0);
        assertThat(r.changePct()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void rangeApplyKeepsLatestAndTrimsWindow() {
        var s = annual(30, i -> i);
        var y5 = Range.Y5.apply(s);
        assertThat(y5.points()).hasSize(6); // 5 años de cambio = 6 puntos anuales
        assertThat(y5.latest().orElseThrow().value()).isEqualTo(29.0);
        assertThat(Range.ALL.apply(s).points()).hasSize(30);
        assertThat(Range.M6.apply(monthly(24, i -> i)).points()).hasSize(7); // 6 meses de cambio = 7 puntos
        assertThat(Range.Y1.apply(monthly(24, i -> i)).points()).hasSize(13);
    }

    private static Series daily(int n) {
        List<Point> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) pts.add(new Point(LocalDate.of(2026, 1, 1).plusDays(i), i));
        return new Series("d", "ambiente-energia", "D", "C", "t", "u", "diaria", pts, Instant.EPOCH);
    }

    @Test
    void dailyRangesCutByDaysInclusive() {
        var s = daily(100);
        assertThat(Range.D7.apply(s).points()).hasSize(8);   // 7 días de cambio = 8 puntos
        assertThat(Range.D30.apply(s).points()).hasSize(31);
        assertThat(Range.D90.apply(s).points()).hasSize(91);
        assertThat(Range.D30.apply(s).latest().orElseThrow().value()).isEqualTo(99.0);
        assertThat(Range.D90.apply(daily(10)).points()).hasSize(10); // ventana mayor que los datos: todo
    }

    @Test
    void dailyAvailableRanges() {
        assertThat(Range.availableFor("diaria")).containsExactly(Range.D7, Range.D30, Range.D90, Range.Y1, Range.ALL);
        assertThat(Range.parse("30d")).contains(Range.D30);
        assertThat(Range.availableFor("mensual")).doesNotContain(Range.D7);
    }

    @Test
    void availableAndParse() {
        assertThat(Range.availableFor("mensual")).containsExactly(Range.M3, Range.M6, Range.Y1, Range.Y2, Range.ALL);
        assertThat(Range.availableFor("anual")).containsExactly(Range.Y5, Range.Y10, Range.ALL);
        assertThat(Range.parse("6m")).contains(Range.M6);
        assertThat(Range.parse("todo")).contains(Range.ALL);
        assertThat(Range.parse("xx")).isEmpty();
    }
}
