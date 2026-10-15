package top.eridu.opencolombia.live;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Datos «en vivo». {@code errors} indica qué fuente falló en el último intento; el valor previo se conserva.
 * {@code fetchedAt} es la última vez que al menos una fuente respondió.
 */
public record LiveSnapshot(UsdCop usdCop, List<Quake> quakes, Instant fetchedAt, Map<String, String> errors) {
    public LiveSnapshot {
        quakes = quakes == null ? List.of() : quakes;
        errors = errors == null ? Map.of() : errors;
    }

    public static LiveSnapshot empty() {
        return new LiveSnapshot(null, List.of(), null, Map.of());
    }
}
