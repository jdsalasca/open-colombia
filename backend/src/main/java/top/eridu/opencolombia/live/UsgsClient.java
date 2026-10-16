package top.eridu.opencolombia.live;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.source.SourceException;

/**
 * Sismos del catálogo USGS, M >= 2,5, últimos 30 días. El rectángulo geográfico de la consulta también cubre
 * partes de Ecuador, Perú, Venezuela, Brasil y Panamá, así que solo se conservan los eventos que USGS ubica
 * en Colombia (su campo {@code place} termina en «, Colombia»). Los eventos en el mar sin país se omiten.
 */
@Component
public class UsgsClient implements QuakeSource {
    private static final String BASE = "https://earthquake.usgs.gov/fdsnws/event/1/query"
            + "?format=geojson&minlatitude=-4.5&maxlatitude=13.5&minlongitude=-79.5&maxlongitude=-66"
            + "&minmagnitude=2.5&orderby=time&limit=200&starttime=";

    private final RestClient http;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public UsgsClient(RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    @Override
    public List<Quake> fetch() throws SourceException {
        String url = BASE + LocalDate.now(clock).minusDays(30);
        String body;
        try {
            body = http.get().uri(URI.create(url)).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("USGS no respondió", e);
        }
        JsonNode features;
        try {
            features = mapper.readTree(body == null ? "{}" : body).get("features");
        } catch (RuntimeException e) {
            throw new SourceException("Respuesta ilegible de USGS", e);
        }
        List<Quake> out = new ArrayList<>();
        if (features != null && features.isArray()) {
            for (JsonNode f : features) {
                try {
                    JsonNode p = f.get("properties");
                    JsonNode c = f.get("geometry").get("coordinates");
                    if (p.get("mag").isNull()) continue;
                    if (!p.get("place").asString().toLowerCase().contains("colombia")) continue;
                    out.add(new Quake(Instant.ofEpochMilli(p.get("time").asLong()), p.get("mag").asDouble(),
                            p.get("place").asString(), c.get(1).asDouble(), c.get(0).asDouble(), c.get(2).asDouble()));
                } catch (RuntimeException ignored) {
                    // evento incompleto: se omite
                }
            }
        }
        out.sort(Comparator.comparing(Quake::time).reversed());
        return out;
    }
}
