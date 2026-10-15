package top.eridu.opencolombia.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Series(String id, String theme, String name, String unit, String source,
                     String sourceUrl, String periodicity, List<Point> points, Instant fetchedAt,
                     Freshness freshness) {

    /** Forma sin frescura (la que persiste la caché y usan los conectores). */
    public Series(String id, String theme, String name, String unit, String source, String sourceUrl,
                  String periodicity, List<Point> points, Instant fetchedAt) {
        this(id, theme, name, unit, source, sourceUrl, periodicity, points, fetchedAt, null);
    }

    public Optional<Point> latest() {
        return points.stream().max(Comparator.comparing(Point::date));
    }

    public Series withPoints(List<Point> newPoints) {
        return new Series(id, theme, name, unit, source, sourceUrl, periodicity, newPoints, fetchedAt, freshness);
    }

    public Series withFreshness(Freshness f) {
        return new Series(id, theme, name, unit, source, sourceUrl, periodicity, points, fetchedAt, f);
    }
}
