package top.eridu.opencolombia.analytics;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import top.eridu.opencolombia.model.Series;

/** Ventanas de tiempo sobre una serie, medidas hacia atrás desde su último dato. */
public enum Range {
    D7("7d", 0, 7), D30("30d", 0, 30), D90("90d", 0, 90),
    M3("3m", 3, 0), M6("6m", 6, 0), Y1("1a", 12, 0), Y2("2a", 24, 0), Y5("5a", 60, 0), Y10("10a", 120, 0),
    ALL("todo", 0, 0);

    private final String code;
    private final int months;
    private final int days;

    Range(String code, int months, int days) {
        this.code = code;
        this.months = months;
        this.days = days;
    }

    public String code() { return code; }

    /** Duración en meses; 0 para {@link #ALL} y los rangos por días. */
    public int months() { return months; }

    /** Duración en días; 0 para los rangos por meses. */
    public int days() { return days; }

    public static Optional<Range> parse(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }

    /** Diarias: 7d, 30d, 90d, 1a, todo. Mensuales: 3m, 6m, 1a, 2a, todo. Anuales: 5a, 10a, todo. */
    public static List<Range> availableFor(String periodicity) {
        return switch (periodicity == null ? "" : periodicity) {
            case "diaria" -> List.of(D7, D30, D90, Y1, ALL);
            case "mensual" -> List.of(M3, M6, Y1, Y2, ALL);
            default -> List.of(Y5, Y10, ALL);
        };
    }

    /** Puntos desde (último − duración) inclusive; el último dato siempre se conserva. */
    public Series apply(Series s) {
        if (this == ALL) return s;
        return s.latest().map(l -> {
            LocalDate from = days > 0 ? l.date().minusDays(days) : l.date().minusMonths(months);
            // inclusivo: "1a" incluye el dato de hace 12 meses, para que la variación sea de 12 meses completos
            return s.withPoints(s.points().stream().filter(p -> !p.date().isBefore(from)).toList());
        }).orElse(s);
    }
}
