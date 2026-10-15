package top.eridu.opencolombia.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Qué tan actual es una serie, medido con la fecha real de su último dato (no con la de la consulta).
 * {@code stale} es true cuando el último dato es más viejo que lo esperado para su periodicidad.
 */
public record Freshness(LocalDate lastDataDate, long ageDays, int staleAfterDays, boolean stale) {
    public static Freshness of(Series s, int staleAfterDays, LocalDate today) {
        return s.latest().map(Point::date)
                .map(d -> {
                    long age = Math.max(0, ChronoUnit.DAYS.between(d, today));
                    return new Freshness(d, age, staleAfterDays, age > staleAfterDays);
                })
                .orElse(new Freshness(null, -1, staleAfterDays, true));
    }
}
