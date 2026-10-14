package top.eridu.opencolombia.breakdown;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.analytics.Range;
import top.eridu.opencolombia.source.SourceException;

/** Composición de homicidios (datos.gov.co, Policía Nacional) por sexo, zona, arma o departamento. */
@Service
public class BreakdownService {
    private static final String DATASET = "https://www.datos.gov.co/resource/m8fd-ahd9.json";
    private static final Duration TTL = Duration.ofHours(1);
    private static final Map<String, String> COLUMN = Map.of(
            "sexo", "sexo", "zona", "zona", "arma", "arma_medio", "departamento", "departamento");
    private static final String OTHERS = "OTROS";

    private record Entry(Instant at, Breakdown value) {}

    private record Latest(Instant at, LocalDate date) {}

    private volatile Latest latest;

    private final RestClient http;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public BreakdownService(RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    public static boolean isDimension(String dim) {
        return COLUMN.containsKey(dim);
    }

    public Breakdown get(String dim, Range range) throws SourceException {
        String column = COLUMN.get(dim);
        if (column == null) throw new IllegalArgumentException("dimensión desconocida: " + dim);
        String key = dim + "|" + range.code();
        Entry hit = cache.get(key);
        Instant now = clock.instant();
        if (hit != null && Duration.between(hit.at(), now).compareTo(TTL) < 0) return hit.value();

        // la ventana se ancla en el último dato del dataset (publica con rezago), no en la fecha de hoy
        LocalDate to = lastDate(now);
        LocalDate from = to.minusMonths(range.months()).plusDays(1);
        URI uri = UriComponentsBuilder.fromUriString(DATASET)
                .queryParam("$select", column + " as k, sum(cantidad) as v")
                .queryParam("$where", "fecha_hecho >= '" + from + "'")
                .queryParam("$group", "k")
                .queryParam("$order", "v DESC")
                .queryParam("$limit", "50")
                .build().encode().toUri();
        String body;
        try {
            body = http.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("datos.gov.co no respondió para la composición", e);
        }
        Breakdown b = parse(dim, range, from, to, uri.toString(), body);
        cache.put(key, new Entry(now, b));
        return b;
    }

    /** Fecha máxima de fecha_hecho en el dataset, cacheada con el mismo TTL que las composiciones. */
    private LocalDate lastDate(Instant now) throws SourceException {
        Latest l = latest;
        if (l != null && Duration.between(l.at(), now).compareTo(TTL) < 0) return l.date();
        URI uri = UriComponentsBuilder.fromUriString(DATASET)
                .queryParam("$select", "max(fecha_hecho) as m")
                .build().encode().toUri();
        String body;
        try {
            body = http.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("datos.gov.co no respondió para la fecha del dataset", e);
        }
        try {
            JsonNode root = mapper.readTree(body == null ? "[]" : body);
            JsonNode m = root.isArray() && root.size() > 0 ? root.get(0).get("m") : null;
            if (m == null || m.isNull() || m.asString().length() < 10) throw new SourceException("dataset sin fechas");
            LocalDate d = LocalDate.parse(m.asString().substring(0, 10));
            latest = new Latest(now, d);
            return d;
        } catch (RuntimeException e) {
            throw new SourceException("Fecha del dataset ilegible", e);
        }
    }

    private Breakdown parse(String dim, Range range, LocalDate from, LocalDate to, String url, String body)
            throws SourceException {
        JsonNode root;
        try {
            root = mapper.readTree(body == null ? "[]" : body);
        } catch (RuntimeException e) {
            throw new SourceException("Respuesta ilegible de datos.gov.co", e);
        }
        record Raw(String label, double value) {}
        List<Raw> raw = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode row : root) {
                JsonNode k = row.get("k");
                JsonNode v = row.get("v");
                if (k == null || k.isNull() || v == null || v.isNull()) continue;
                String label = k.asString().trim();
                double value;
                try {
                    value = Double.parseDouble(v.asString().trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (!label.isEmpty() && value > 0) raw.add(new Raw(label, value));
            }
        }
        raw.sort(Comparator.comparingDouble(Raw::value).reversed());
        int keep = dim.equals("departamento") ? 8 : 6;
        List<Raw> top = new ArrayList<>(raw.subList(0, Math.min(keep, raw.size())));
        if (raw.size() > keep) {
            top.add(new Raw(OTHERS, raw.subList(keep, raw.size()).stream().mapToDouble(Raw::value).sum()));
        }
        double total = top.stream().mapToDouble(Raw::value).sum();
        List<Breakdown.Item> items = top.stream()
                .map(r -> new Breakdown.Item(r.label(), r.value(), total == 0 ? 0 : r.value() / total)).toList();
        return new Breakdown(dim, range.code(), from, to, total, items, "datos.gov.co", url);
    }
}
