package top.eridu.opencolombia.source;

import java.util.Map;
import top.eridu.opencolombia.model.Theme;

/**
 * Definición de un indicador. Para WORLD_BANK, {@code ref} es el código del indicador; para SODA es el
 * id del dataset y {@code params} los parámetros SoQL ($select, $where, $group, $order, $limit).
 */
public record IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity,
                            Kind kind, String ref, Map<String, String> params,
                            String valueField, String dateField) {
    public enum Kind { WORLD_BANK, SODA }
}
