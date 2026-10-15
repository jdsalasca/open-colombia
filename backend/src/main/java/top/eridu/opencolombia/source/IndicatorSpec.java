package top.eridu.opencolombia.source;

import java.util.List;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;
import top.eridu.opencolombia.model.Freshness;
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
                            String description, String method, Boolean higherIsBetter,
                            RefreshClass refresh, int staleAfterDays) {
    public enum Kind { WORLD_BANK, SODA, XM }

    /** Con qué frecuencia conviene refrescar el indicador. */
    public enum RefreshClass { LIVE, DAILY, SLOW }

    private static final String WB_API =
            "https://api.worldbank.org/v2/country/COL/indicator/%s?format=json&per_page=200";
    private static final String SODA_API = "https://www.datos.gov.co/resource/%s.json";

    /** Forma intermedia (v2): refresco y vencimiento por defecto según la periodicidad. */
    public IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity, Kind kind,
                         String ref, Map<String, String> params, String valueField, String dateField,
                         String description, String method, Boolean higherIsBetter) {
        this(id, theme, name, unit, periodicity, kind, ref, params, valueField, dateField, description, method,
                higherIsBetter, defaultRefresh(periodicity), defaultStaleAfterDays(periodicity));
    }

    /** Forma corta (v1): sin metodología ni semántica. */
    public IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity, Kind kind,
                         String ref, Map<String, String> params, String valueField, String dateField) {
        this(id, theme, name, unit, periodicity, kind, ref, params, valueField, dateField, name, "", null);
    }

    static RefreshClass defaultRefresh(String periodicity) {
        return "diaria".equals(periodicity) || "mensual".equals(periodicity) ? RefreshClass.DAILY : RefreshClass.SLOW;
    }

    /**
     * Vencimiento según el rezago real de publicación. Anual: un dato fechado el 1 de enero del año Y sigue
     * siendo el más reciente posible hasta ~3 años después (1.100 días); mensual: 100 días; diaria: 7.
     */
    static int defaultStaleAfterDays(String periodicity) {
        return switch (periodicity == null ? "" : periodicity) {
            case "diaria" -> 7;
            case "mensual" -> 100;
            default -> 1100;
        };
    }

    /** Adjunta la frescura de la serie según el vencimiento de este indicador. */
    public Series freshen(Series s, java.time.LocalDate today) {
        return s.withFreshness(Freshness.of(s, staleAfterDays, today));
    }

    public String sourceName() {
        return switch (kind) {
            case WORLD_BANK -> "Banco Mundial";
            case XM -> "XM";
            case SODA -> "datos.gov.co";
        };
    }

    public String sourceUrl() {
        return switch (kind) {
            case WORLD_BANK -> "https://data.worldbank.org/indicator/%s?locations=CO".formatted(ref);
            case XM -> "https://www.xm.com.co/";
            case SODA -> "https://www.datos.gov.co/d/%s".formatted(ref);
        };
    }

    /** Cuerpo JSON del POST a XM para una ventana. */
    static String xmBody(String metricId, String entity, java.time.LocalDate from, java.time.LocalDate toExclusive) {
        return "{\"MetricId\":\"%s\",\"StartDate\":\"%s\",\"EndDate\":\"%s\",\"Entity\":\"%s\",\"Filter\":[]}"
                .formatted(metricId, from, toExclusive, entity);
    }

    /** Cuerpo de la primera consulta (solo XM; null en las demás fuentes): la metodología lo muestra. */
    public String queryBody(java.time.LocalDate today) {
        if (kind != Kind.XM) return null;
        int windowDays = Integer.parseInt(params.getOrDefault("windowDays", "120"));
        var first = XmConnector.windowsFor(today, windowDays).get(0);
        return xmBody(ref, params.getOrDefault("entity", "Sistema"), first.from(), first.toExclusive());
    }

    /** URL exacta que se consulta a la fuente hoy: la usan el conector y la página de metodología. */
    public String queryUrl() {
        return queryUrl(java.time.LocalDate.now());
    }

    /**
     * URL exacta que se consulta a la fuente en la fecha {@code today}. Las claves de {@code params} que no
     * empiezan con "$" son banderas internas y no viajan; {@code sinceDaysAgo} + {@code sinceField} se
     * traducen a una ventana móvil en el {@code $where}.
     */
    public String queryUrl(java.time.LocalDate today) {
        if (kind == Kind.WORLD_BANK) return WB_API.formatted(ref);
        if (kind == Kind.XM) return XmConnector.ENDPOINT;
        String days = params.get("sinceDaysAgo");
        boolean windowed = days != null && params.get("sinceField") != null;
        return sodaUrl(windowed ? today.minusDays(Long.parseLong(days)) : null, null);
    }

    /**
     * URLs que realmente se piden a la fuente. Si el indicador declara {@code chunkDays}, la ventana móvil se
     * parte en bloques contiguos de ese tamaño (cada uno cabe en el timeout de la fuente); si no, es una sola
     * consulta ({@link #queryUrl(java.time.LocalDate)}).
     */
    public java.util.List<String> chunkUrls(java.time.LocalDate today) {
        String chunk = params.get("chunkDays");
        String days = params.get("sinceDaysAgo");
        if (kind != Kind.SODA || chunk == null || days == null || params.get("sinceField") == null) {
            return java.util.List.of(queryUrl(today));
        }
        int size = Integer.parseInt(chunk);
        java.time.LocalDate start = today.minusDays(Long.parseLong(days));
        java.time.LocalDate end = today.plusDays(1); // exclusivo: incluye hoy
        java.util.List<String> urls = new java.util.ArrayList<>();
        java.time.LocalDate from = start;
        // el último bloque absorbe el sobrante (hasta 1,5 veces el tamaño) para no pedir un bloque de 1 día
        while (java.time.temporal.ChronoUnit.DAYS.between(from, end) > size * 3 / 2) {
            urls.add(sodaUrl(from, from.plusDays(size)));
            from = from.plusDays(size);
        }
        urls.add(sodaUrl(from, end));
        return urls;
    }

    private String sodaUrl(java.time.LocalDate from, java.time.LocalDate toExclusive) {
        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(SODA_API.formatted(ref));
        Map<String, String> q = new java.util.LinkedHashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("$")) q.put(k, v);
        });
        String field = params.get("sinceField");
        if (from != null && field != null) {
            String where = field + " >= '" + from + "T00:00:00'";
            if (toExclusive != null) where += " AND " + field + " < '" + toExclusive + "T00:00:00'";
            String existing = q.get("$where");
            q.put("$where", existing == null ? where : "(" + existing + ") AND " + where);
        }
        q.forEach(ub::queryParam);
        return ub.build().encode().toUriString();
    }

    /** Serie vacía para un indicador que aún no cargó: la UI la muestra como «sin dato». */
    public Series placeholder() {
        return new Series(id, theme.id(), name, unit, sourceName(), sourceUrl(), periodicity, List.of(), null);
    }
}
