package top.eridu.opencolombia.source;

import java.time.LocalDate;
import tools.jackson.databind.JsonNode;

/** Lectura tolerante de valores de fuentes abiertas: null, vacío y "." se tratan como ausentes. */
final class Values {
    private Values() {}

    static Double number(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        if (n.isNumber()) return n.asDouble();
        String s = n.asString().trim();
        if (s.isEmpty() || s.equals(".")) return null;
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "2025" -> 2025-01-01. */
    static LocalDate year(JsonNode n) {
        if (n == null || n.isNull()) return null;
        try {
            return LocalDate.of(Integer.parseInt(n.asString().trim()), 1, 1);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "2026-08-01T00:00:00.000" -> 2026-08-01. */
    static LocalDate isoDate(JsonNode n) {
        if (n == null || n.isNull()) return null;
        String s = n.asString();
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
