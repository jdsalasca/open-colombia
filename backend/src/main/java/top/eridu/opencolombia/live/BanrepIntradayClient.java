package top.eridu.opencolombia.live;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.source.SourceException;

/**
 * TRM intradía del Banco de la República. Es el endpoint que usa su propio sitio: público pero sin contrato
 * oficial, por eso cualquier cambio de formato degrada a «sin dato» y nunca rompe el resto.
 */
@Component
public class BanrepIntradayClient implements UsdCopSource {
    static final String URL =
            "https://totoro.banrep.gov.co/estadisticas-economicas/rest/consultaDatosService/consultaMercadoCambiario";
    private static final int MAX_TICKS = 120;

    private final RestClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public BanrepIntradayClient(RestClient http) {
        this.http = http;
    }

    @Override
    public Optional<UsdCop> fetch() throws SourceException {
        String body;
        try {
            body = http.get().uri(URI.create(URL)).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new SourceException("Banco de la República no respondió", e);
        }
        JsonNode root;
        try {
            root = mapper.readTree(body == null ? "[]" : body);
        } catch (RuntimeException e) {
            throw new SourceException("Respuesta ilegible del Banco de la República", e);
        }
        List<Tick> all = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode row : root) {
                if (!row.isArray() || row.size() < 2) continue;
                try {
                    long ms = Long.parseLong(row.get(0).asString().trim());
                    double v = Double.parseDouble(row.get(1).asString().trim());
                    if (Double.isFinite(v) && v > 0) all.add(new Tick(Instant.ofEpochMilli(ms), v));
                } catch (RuntimeException ignored) {
                    // fila ilegible: se omite
                }
            }
        }
        if (all.isEmpty()) return Optional.empty();
        all.sort(Comparator.comparing(Tick::t));
        double open = all.get(0).v();
        Tick last = all.get(all.size() - 1);
        return Optional.of(new UsdCop(downsample(all), last.v(), open, (last.v() - open) / open * 100.0, last.t()));
    }

    /** Muestreo uniforme que conserva siempre el primer y el último dato. */
    static List<Tick> downsample(List<Tick> ticks) {
        if (ticks.size() <= MAX_TICKS) return List.copyOf(ticks);
        List<Tick> out = new ArrayList<>(MAX_TICKS);
        for (int i = 0; i < MAX_TICKS; i++) {
            out.add(ticks.get((int) Math.round(i * (ticks.size() - 1) / (double) (MAX_TICKS - 1))));
        }
        return out;
    }
}
