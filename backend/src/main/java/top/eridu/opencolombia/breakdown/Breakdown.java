package top.eridu.opencolombia.breakdown;

import java.time.LocalDate;
import java.util.List;

/** Composición por categorías entre {@code from} y {@code to} (ambos inclusive). {@code share} va de 0 a 1. */
public record Breakdown(String dim, String range, LocalDate from, LocalDate to, double total, List<Item> items,
                        String source, String queryUrl) {
    public record Item(String label, double value, double share) {}
}
