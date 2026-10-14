package top.eridu.opencolombia.analytics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

/** Estadística básica sobre series. Con menos de {@value #MIN_N} observaciones no se concluye nada. */
public final class Stats {
    public static final int MIN_N = 5;

    private Stats() {}

    private static double years(LocalDate from, LocalDate d) {
        return ChronoUnit.DAYS.between(from, d) / 365.25;
    }

    private static double safeR2(SimpleRegression r) {
        double v = r.getRSquare();
        return Double.isNaN(v) ? 0.0 : v;
    }

    /** MCO del valor contra el tiempo (x = años desde el primer dato). */
    public static Optional<OlsResult> trend(Series s) {
        List<Point> p = s.points();
        if (p.size() < MIN_N) return Optional.empty();
        LocalDate t0 = p.stream().map(Point::date).min(LocalDate::compareTo).orElseThrow();
        var reg = new SimpleRegression();
        for (Point q : p) reg.addData(years(t0, q.date()), q.value());
        return Optional.of(new OlsResult(reg.getSlope(), reg.getIntercept(), safeR2(reg), p.size()));
    }

    /** Variación porcentual del último dato frente al de un año antes (misma fecha menos 1 año). */
    public static Optional<Double> yoyChange(Series s) {
        Optional<Point> last = s.latest();
        if (last.isEmpty()) return Optional.empty();
        LocalDate prevDate = last.get().date().minusYears(1);
        return s.points().stream().filter(q -> q.date().equals(prevDate)).findFirst()
                .filter(q -> q.value() != 0)
                .map(q -> (last.get().value() - q.value()) / Math.abs(q.value()) * 100.0);
    }

    private static Map<Integer, Double> byYear(Series s) {
        Map<Integer, Double> m = new TreeMap<>();
        s.points().forEach(p -> m.merge(p.date().getYear(), p.value(), (a, b) -> b));
        return m;
    }

    private record Pairs(double[] x, double[] y) {}

    private static Optional<Pairs> align(Series a, Series b) {
        Map<Integer, Double> ma = byYear(a), mb = byYear(b);
        var years = ma.keySet().stream().filter(mb::containsKey).toList();
        if (years.size() < MIN_N) return Optional.empty();
        double[] x = years.stream().mapToDouble(ma::get).toArray();
        double[] y = years.stream().mapToDouble(mb::get).toArray();
        return Optional.of(new Pairs(x, y));
    }

    public static Optional<Double> correlation(Series a, Series b) {
        return align(a, b).map(p -> new PearsonsCorrelation().correlation(p.x(), p.y()))
                .filter(v -> !Double.isNaN(v));
    }

    /** Solo las observaciones de los últimos {@code years} años respecto del dato más reciente. */
    public static Series recent(Series s, int years) {
        return s.latest().map(l -> {
            LocalDate from = l.date().minusYears(years);
            return s.withPoints(s.points().stream().filter(p -> p.date().isAfter(from)).toList());
        }).orElse(s);
    }

    /**
     * Correlación entre las variaciones anuales (primeras diferencias) de dos series, alineadas por año.
     * Distingue co-movimiento real de dos series que solo comparten tendencia.
     */
    public static Optional<Double> differenceCorrelation(Series a, Series b) {
        Map<Integer, Double> ma = byYear(a), mb = byYear(b);
        var years = ma.keySet().stream().filter(mb::containsKey).toList();
        var dx = new java.util.ArrayList<Double>();
        var dy = new java.util.ArrayList<Double>();
        for (int i = 1; i < years.size(); i++) {
            if (years.get(i) - years.get(i - 1) != 1) continue; // solo años consecutivos
            dx.add(ma.get(years.get(i)) - ma.get(years.get(i - 1)));
            dy.add(mb.get(years.get(i)) - mb.get(years.get(i - 1)));
        }
        if (dx.size() < MIN_N) return Optional.empty();
        double v = new PearsonsCorrelation().correlation(
                dx.stream().mapToDouble(Double::doubleValue).toArray(),
                dy.stream().mapToDouble(Double::doubleValue).toArray());
        return Double.isNaN(v) ? Optional.empty() : Optional.of(v);
    }

    /** Regresión simple de y sobre x, alineando por año calendario. */
    public static Optional<OlsResult> regress(Series x, Series y) {
        return align(x, y).map(p -> {
            var reg = new SimpleRegression();
            for (int i = 0; i < p.x().length; i++) reg.addData(p.x()[i], p.y()[i]);
            return new OlsResult(reg.getSlope(), reg.getIntercept(), safeR2(reg), p.x().length);
        });
    }

    /** Proyección lineal a {@code yearsAhead} años del último dato, con intervalo de predicción al 95%. */
    public static Optional<Forecast> forecast(Series s, int yearsAhead) {
        List<Point> p = s.points();
        if (p.size() < MIN_N) return Optional.empty();
        LocalDate t0 = p.stream().map(Point::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate tLast = p.stream().map(Point::date).max(LocalDate::compareTo).orElseThrow();
        var reg = new SimpleRegression();
        double sumX = 0;
        for (Point q : p) {
            double x = years(t0, q.date());
            reg.addData(x, q.value());
            sumX += x;
        }
        int n = p.size();
        double xMean = sumX / n;
        double x0 = years(t0, tLast) + yearsAhead;
        double value = reg.predict(x0);
        double sxx = reg.getXSumSquares();
        double mse = reg.getMeanSquareError();
        if (Double.isNaN(value) || sxx == 0) return Optional.empty();
        double t = new TDistribution(n - 2).inverseCumulativeProbability(0.975);
        double se = Math.sqrt(Math.max(0, mse) * (1 + 1.0 / n + Math.pow(x0 - xMean, 2) / sxx));
        return Optional.of(new Forecast(value, value - t * se, value + t * se));
    }
}
