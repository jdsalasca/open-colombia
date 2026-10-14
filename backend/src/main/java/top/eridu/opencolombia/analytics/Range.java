package top.eridu.opencolombia.analytics;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import top.eridu.opencolombia.model.Series;

/** Ventanas de tiempo sobre una serie, medidas hacia atrás desde su último dato. */
public enum Range {
    M3("3m", 3), M6("6m", 6), Y1("1a", 12), Y2("2a", 24), Y5("5a", 60), Y10("10a", 120), ALL("todo", 0);

    private final String code;
    private final int months;

    Range(String code, int months) {
        this.code = code;
        this.months = months;
    }

    public String code() { return code; }

    /** Duración en meses; 0 para {@link #ALL}. */
    public int months() { return months; }

    public static Optional<Range> parse(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }

    /** Mensuales: 3m, 6m, 1a, 2a, todo. Anuales (y cualquier otra): 5a, 10a, todo. */
    public static List<Range> availableFor(String periodicity) {
        return "mensual".equals(periodicity)
                ? List.of(M3, M6, Y1, Y2, ALL)
                : List.of(Y5, Y10, ALL);
    }

    /** Puntos con fecha posterior a (último − duración); el último dato siempre se conserva. */
    public Series apply(Series s) {
        if (this == ALL) return s;
        return s.latest().map(l -> {
            LocalDate from = l.date().minusMonths(months);
            return s.withPoints(s.points().stream().filter(p -> p.date().isAfter(from)).toList());
        }).orElse(s);
    }
}
