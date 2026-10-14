package top.eridu.opencolombia.source;

import java.util.List;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;

/**
 * Definición de un indicador. Para WORLD_BANK, {@code ref} es el código del indicador; para SODA es el
 * id del dataset y {@code params} los parámetros SoQL ($select, $where, $group, $order, $limit); las
 * claves que no empiezan con "$" son banderas internas y no viajan a la fuente.
 *
 * <p>{@code higherIsBetter}: true/false si subir es bueno/malo; null si es neutro.
 */
public record IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity,
                            Kind kind, String ref, Map<String, String> params,
                            String valueField, String dateField,
                            String description, String method, Boolean higherIsBetter) {
    public enum Kind { WORLD_BANK, SODA }

    private static final String WB_API =
            "https://api.worldbank.org/v2/country/COL/indicator/%s?format=json&per_page=200";
    private static final String SODA_API = "https://www.datos.gov.co/resource/%s.json";

    /** Forma corta (v1): sin metodología ni semántica. */
    public IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity, Kind kind,
                         String ref, Map<String, String> params, String valueField, String dateField) {
        this(id, theme, name, unit, periodicity, kind, ref, params, valueField, dateField, name, "", null);
    }

    public String sourceName() {
        return kind == Kind.WORLD_BANK ? "Banco Mundial" : "datos.gov.co";
    }

    public String sourceUrl() {
        return kind == Kind.WORLD_BANK
                ? "https://data.worldbank.org/indicator/%s?locations=CO".formatted(ref)
                : "https://www.datos.gov.co/d/%s".formatted(ref);
    }

    /** URL exacta que se consulta a la fuente: la usan el conector y la página de metodología. */
    public String queryUrl() {
        if (kind == Kind.WORLD_BANK) return WB_API.formatted(ref);
        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(SODA_API.formatted(ref));
        params.forEach((k, v) -> {
            if (k.startsWith("$")) ub.queryParam(k, v);
        });
        return ub.build().encode().toUriString();
    }

    /** Serie vacía para un indicador que aún no cargó: la UI la muestra como «sin dato». */
    public Series placeholder() {
        return new Series(id, theme.id(), name, unit, sourceName(), sourceUrl(), periodicity, List.of(), null);
    }
}
