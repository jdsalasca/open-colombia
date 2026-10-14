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
        URI uri = URI.create(spec.queryUrl());
        String body;
        try {
            body = http.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("datos.gov.co no respondió para " + spec.ref(), e);
        }
        return new Series(spec.id(), spec.theme().id(), spec.name(), spec.unit(), "datos.gov.co",
                spec.sourceUrl(), spec.periodicity(), parse(body, spec), clock.instant());
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
        for (JsonNode row : root) {
            Double v = Values.number(row.get(spec.valueField()));
            LocalDate d = Values.isoDate(row.get(spec.dateField()));
            if (v != null && d != null) out.add(new Point(d, v));
        }
        if ("true".equals(spec.params().get("dropCurrentPeriod"))) {
            YearMonth now = YearMonth.now(clock);
            out.removeIf(p -> YearMonth.from(p.date()).equals(now)); // mes en curso: siempre parcial
        }
        out.sort(Comparator.comparing(Point::date));
        return out;
    }
}
