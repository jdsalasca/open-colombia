package top.eridu.opencolombia.breakdown;

import java.time.LocalDate;
import java.util.List;

/** Composición por categorías en una ventana de tiempo. {@code share} va de 0 a 1. */
public record Breakdown(String dim, String range, LocalDate from, double total, List<Item> items,
                        String source, String queryUrl) {
    public record Item(String label, double value, double share) {}
}
