package top.eridu.opencolombia.source;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

/**
 * Métricas horarias de XM (operador del mercado eléctrico) agregadas a día. API pública sin contrato oficial:
 * una consulta cubre como máximo 31 días, así que la ventana se parte en bloques.
 */
@Component
public class XmConnector implements SourceConnector {
    static final String ENDPOINT = "https://servapibi.xm.com.co/hourly";
    private static final int MAX_DAYS = 31;
    private static final int MIN_HOURS_FOR_AVERAGE = 20;

    /** Bloque de consulta: {@code toExclusive} es el día siguiente al último día pedido. */
    public record Window(LocalDate from, LocalDate toExclusive) {}

    private final RestClient http;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public XmConnector(RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public boolean supports(IndicatorSpec spec) {
        return spec.kind() == IndicatorSpec.Kind.XM;
    }

    /** Bloques contiguos de a lo sumo 31 días que cubren [hoy - windowDays, hoy] (hoy incluido). */
    public static List<Window> windowsFor(LocalDate today, int windowDays) {
        LocalDate end = today.plusDays(1);
        List<Window> out = new ArrayList<>();
        for (LocalDate from = today.minusDays(windowDays); from.isBefore(end); from = from.plusDays(MAX_DAYS)) {
            LocalDate to = from.plusDays(MAX_DAYS).isAfter(end) ? end : from.plusDays(MAX_DAYS);
            out.add(new Window(from, to));
        }
        return out;
    }

    @Override
    public Series fetch(IndicatorSpec spec) throws SourceException {
        String entity = spec.params().getOrDefault("entity", "Sistema");
        boolean sum = "sum".equals(spec.params().get("agg"));
        double scale = Double.parseDouble(spec.params().getOrDefault("scale", "1"));
        int windowDays = Integer.parseInt(spec.params().getOrDefault("windowDays", "120"));
        Map<LocalDate, Double> byDay = new TreeMap<>();
        for (Window w : windowsFor(LocalDate.now(clock), windowDays)) {
            parse(post(spec, entity, w), sum, scale, byDay);
        }
        List<Point> points = new ArrayList<>();
        byDay.forEach((d, v) -> points.add(new Point(d, v)));
        points.sort(Comparator.comparing(Point::date));
        if ("true".equals(spec.params().get("dropCurrentDay"))) {
            LocalDate today = LocalDate.now(clock);
            points.removeIf(p -> p.date().equals(today));
        }
        return new Series(spec.id(), spec.theme().id(), spec.name(), spec.unit(), spec.sourceName(),
                spec.sourceUrl(), spec.periodicity(), points, clock.instant());
    }

    private String post(IndicatorSpec spec, String entity, Window w) throws SourceException {
        String body = IndicatorSpec.xmBody(spec.ref(), entity, w.from(), w.toExclusive());
        try {
            return http.post().uri(ENDPOINT).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("XM no respondió para " + spec.ref(), e);
        }
    }

    private void parse(String body, boolean sum, double scale, Map<LocalDate, Double> out) throws SourceException {
        JsonNode root;
        try {
            root = mapper.readTree(body == null ? "{}" : body);
        } catch (RuntimeException e) {
            throw new SourceException("Respuesta ilegible de XM", e);
        }
        JsonNode items = root.get("Items");
        if (items == null || !items.isArray()) {
            if (root.isObject() && root.has("Metric")) return; // sin datos para la ventana
            throw new SourceException("Respuesta inesperada de XM");
        }
        for (JsonNode item : items) {
            JsonNode date = item.get("Date");
            JsonNode ents = item.get("HourlyEntities");
            if (date == null || ents == null || !ents.isArray() || ents.isEmpty()) continue;
            JsonNode values = ents.get(0).get("Values");
            if (values == null) continue;
            double total = 0;
            int valid = 0;
            for (int h = 1; h <= 24; h++) {
                Double v = Values.number(values.get(String.format("Hour%02d", h)));
                if (v != null) {
                    total += v;
                    valid++;
                }
            }
            // un promedio tolera algunas horas faltantes; una suma diaria exige las 24
            boolean complete = sum ? valid == 24 : valid >= MIN_HOURS_FOR_AVERAGE;
            if (!complete) continue;
            try {
                LocalDate d = LocalDate.parse(date.asString().substring(0, 10));
                out.put(d, (sum ? total : total / valid) / scale);
            } catch (RuntimeException ignored) {
                // fecha ilegible: se omite el día
            }
        }
    }
}
