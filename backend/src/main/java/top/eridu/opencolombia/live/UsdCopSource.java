package top.eridu.opencolombia.live;

import java.util.Optional;
import top.eridu.opencolombia.source.SourceException;

@FunctionalInterface
public interface UsdCopSource {
    /** Vacío si hoy aún no hay cotizaciones (mercado cerrado o antes de abrir). */
    Optional<UsdCop> fetch() throws SourceException;
}
