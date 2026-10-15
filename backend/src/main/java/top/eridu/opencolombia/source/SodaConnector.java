package top.eridu.opencolombia.source;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

/** Datasets de datos.gov.co (Socrata SODA), agregados en el servidor con SoQL. */
@Component
public class SodaConnector implements SourceConnector {

    private final RestClient http;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public SodaConnector(RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public boolean supports(IndicatorSpec spec) {
        return spec.kind() == IndicatorSpec.Kind.SODA;
    }

    @Override
    public Series fetch(IndicatorSpec spec) throws SourceException {
        List<String> urls = spec.chunkUrls(LocalDate.now(clock));
        List<Point> points = new ArrayList<>();
        if (urls.size() == 1) {
            points.addAll(parse(get(urls.get(0), spec), spec));
        } else {
            // bloques en paralelo: cada uno cabe en el timeout; si uno falla, falla toda la serie
            try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                List<java.util.concurrent.Future<String>> parts = urls.stream()
                        .map(u -> pool.submit(() -> get(u, spec))).toList();
                for (var f : parts) {
                    try {
                        points.addAll(parse(f.get(), spec));
                    } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                        Throwable c = e.getCause() == null ? e : e.getCause();
                        if (c instanceof SourceException se) throw se;
                        throw new SourceException("datos.gov.co no respondió para " + spec.ref(), c);
                    }
                }
            }
        }
        // sin duplicados por fecha (los bloques son contiguos pero la fuente puede repetir un borde)
        java.util.Map<LocalDate, Point> byDate = new java.util.TreeMap<>();
        points.forEach(p -> byDate.put(p.date(), p));
        List<Point> merged = new ArrayList<>(byDate.values());
        return new Series(spec.id(), spec.theme().id(), spec.name(), spec.unit(), "datos.gov.co",
                spec.sourceUrl(), spec.periodicity(), applyFlags(merged, spec), clock.instant());
    }

    private String get(String url, IndicatorSpec spec) throws SourceException {
        try {
            return http.get().uri(URI.create(url)).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("datos.gov.co no respondió para " + spec.ref(), e);
        }
    }

    private List<Point> parse(String body, IndicatorSpec spec) throws SourceException {
        JsonNode root;
        try {
            root = mapper.readTree(body == null ? "[]" : body);
        } catch (RuntimeException e) {
            throw new SourceException("Respuesta ilegible de datos.gov.co", e);
        }
        List<Point> out = new ArrayList<>();
        if (!root.isArray()) return out;
        String divisor = spec.params().get("divideBy");
        for (JsonNode row : root) {
            Double v = Values.number(row.get(spec.valueField()));
            LocalDate d = Values.isoDate(row.get(spec.dateField()));
            if (v != null && divisor != null) {
                Double n = Values.number(row.get(divisor));
                v = n == null || n <= 0 ? null : v / n; // p. ej. lluvia total / número de estaciones
            }
            if (v != null && d != null) out.add(new Point(d, v));
        }
        out.sort(Comparator.comparing(Point::date));
        return out;
    }

    /** Descarta el día o el mes en curso cuando el indicador lo pide (llegan incompletos). */
    private List<Point> applyFlags(List<Point> pts, IndicatorSpec spec) {
        List<Point> out = new ArrayList<>(pts);
        if ("true".equals(spec.params().get("dropCurrentDay"))) {
            LocalDate today = LocalDate.now(clock);
            out.removeIf(p -> p.date().equals(today));
        }
        if ("true".equals(spec.params().get("dropCurrentPeriod"))) {
            YearMonth now = YearMonth.now(clock);
            out.removeIf(p -> YearMonth.from(p.date()).equals(now));
        }
        String lag = spec.params().get("dropLastPeriods");
        if (lag != null) {
            // fuentes que consolidan con rezago: el último periodo publicado llega incompleto y parece una caída
            int n = Integer.parseInt(lag);
            out.sort(Comparator.comparing(Point::date));
            if (out.size() > n) out = new ArrayList<>(out.subList(0, out.size() - n));
        }
        return out;
    }
}
