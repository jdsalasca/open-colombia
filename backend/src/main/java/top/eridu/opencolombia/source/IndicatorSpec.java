package top.eridu.opencolombia.source;

import java.util.Map;
import java.util.List;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;

/**
 * Definición de un indicador. Para WORLD_BANK, {@code ref} es el código del indicador; para SODA es el
 * id del dataset y {@code params} los parámetros SoQL ($select, $where, $group, $order, $limit).
 */
public record IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity,
                            Kind kind, String ref, Map<String, String> params,
                            String valueField, String dateField) {
    public enum Kind { WORLD_BANK, SODA }

    public String sourceName() {
        return kind == Kind.WORLD_BANK ? "Banco Mundial" : "datos.gov.co";
    }

    public String sourceUrl() {
        return kind == Kind.WORLD_BANK
                ? "https://data.worldbank.org/indicator/%s?locations=CO".formatted(ref)
                : "https://www.datos.gov.co/d/%s".formatted(ref);
    }

    /** Serie vacía para un indicador que aún no cargó: la UI la muestra como «sin dato». */
    public Series placeholder() {
        return new Series(id, theme.id(), name, unit, sourceName(), sourceUrl(), periodicity, List.of(), null);
    }
}
