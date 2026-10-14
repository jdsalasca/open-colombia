package top.eridu.opencolombia.source;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
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

/** Indicadores anuales del Banco Mundial para Colombia. */
@Component
public class WorldBankConnector implements SourceConnector {

    private final RestClient http;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public WorldBankConnector(RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public boolean supports(IndicatorSpec spec) {
        return spec.kind() == IndicatorSpec.Kind.WORLD_BANK;
    }

    @Override
    public Series fetch(IndicatorSpec spec) throws SourceException {
        String body;
        try {
            body = http.get().uri(URI.create(spec.queryUrl())).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("Banco Mundial no respondió para " + spec.ref(), e);
        }
        return new Series(spec.id(), spec.theme().id(), spec.name(), spec.unit(), "Banco Mundial",
                spec.sourceUrl(), spec.periodicity(), parse(body), clock.instant());
    }

    private List<Point> parse(String body) throws SourceException {
        JsonNode root;
        try {
            root = mapper.readTree(body == null ? "" : body);
        } catch (RuntimeException e) {
            throw new SourceException("Respuesta ilegible del Banco Mundial", e);
        }
        List<Point> out = new ArrayList<>();
        if (!root.isArray() || root.size() < 2 || !root.get(1).isArray()) return out;
        for (JsonNode row : root.get(1)) {
            Double v = Values.number(row.get("value"));
            LocalDate d = Values.year(row.get("date"));
            if (v != null && d != null) out.add(new Point(d, v));
        }
        out.sort(Comparator.comparing(Point::date));
        return out;
    }
}
