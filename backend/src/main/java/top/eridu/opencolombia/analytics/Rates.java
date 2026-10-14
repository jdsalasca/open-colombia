package top.eridu.opencolombia.analytics;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

public final class Rates {
    /** La anualizada exige un año de cambio completo; la fórmula escala por días reales. */
    private static final long MIN_DAYS_FOR_ANNUALIZED = 360;

    private Rates() {}

    public static Optional<RatesResult> compute(Series s) {
        List<Point> p = s.points().stream().sorted(Comparator.comparing(Point::date)).toList();
        if (p.size() < 2) return Optional.empty();
        Point first = p.get(0);
        Point last = p.get(p.size() - 1);
        double change = last.value() - first.value();
        Double changePct = first.value() == 0 ? null : change / Math.abs(first.value()) * 100.0;

        long days = ChronoUnit.DAYS.between(first.date(), last.date());
        Double annualized = null;
        if (days >= MIN_DAYS_FOR_ANNUALIZED && first.value() > 0 && last.value() > 0) {
            annualized = (Math.pow(last.value() / first.value(), 365.25 / days) - 1) * 100.0;
        }

        var stats = new DescriptiveStatistics();
        p.forEach(q -> stats.addValue(q.value()));
        Point min = p.stream().min(Comparator.comparingDouble(Point::value)).orElseThrow();
        Point max = p.stream().max(Comparator.comparingDouble(Point::value)).orElseThrow();
        double sd = stats.getStandardDeviation();
        return Optional.of(new RatesResult(p.size(), first.date(), last.date(), change, changePct, annualized,
                stats.getMean(), min, max, Double.isNaN(sd) ? 0.0 : sd));
    }
}
