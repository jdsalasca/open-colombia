package top.eridu.opencolombia.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Series(String id, String theme, String name, String unit, String source,
                     String sourceUrl, String periodicity, List<Point> points, Instant fetchedAt,
                     Freshness freshness, String fingerprint) {

    /** Con frescura pero sin huella (series recién leídas de la API). */
    public Series(String id, String theme, String name, String unit, String source, String sourceUrl,
                  String periodicity, List<Point> points, Instant fetchedAt, Freshness freshness) {
        this(id, theme, name, unit, source, sourceUrl, periodicity, points, fetchedAt, freshness, null);
    }

    /** Forma sin frescura (la que persiste la caché y usan los conectores). */
    public Series(String id, String theme, String name, String unit, String source, String sourceUrl,
                  String periodicity, List<Point> points, Instant fetchedAt) {
        this(id, theme, name, unit, source, sourceUrl, periodicity, points, fetchedAt, null);
    }

    public Optional<Point> latest() {
        return points.stream().max(Comparator.comparing(Point::date));
    }

    public Series withPoints(List<Point> newPoints) {
        return new Series(id, theme, name, unit, source, sourceUrl, periodicity, newPoints, fetchedAt, freshness,
                fingerprint);
    }

    public Series withFreshness(Freshness f) {
        return new Series(id, theme, name, unit, source, sourceUrl, periodicity, points, fetchedAt, f, fingerprint);
    }

    /** Huella de la definición del indicador que produjo esta serie (ver IndicatorSpec#fingerprint). */
    public Series withFingerprint(String fp) {
        return new Series(id, theme, name, unit, source, sourceUrl, periodicity, points, fetchedAt, freshness, fp);
    }
}
