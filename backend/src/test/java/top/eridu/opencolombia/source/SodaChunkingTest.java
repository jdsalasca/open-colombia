package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Theme;

/** Las consultas pesadas de IDEAM (12-51 s en 120 días) se parten en bloques que caben en el timeout. */
class SodaChunkingTest {
    private MockRestServiceServer server;
    private SodaConnector connector;
    private IndicatorSpec spec;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        connector = new SodaConnector(builder.build(),
                Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", "date_trunc_ymd(fechaobservacion) as dia, avg(valorobservado) as v");
        p.put("$group", "dia");
        p.put("sinceDaysAgo", "120");
        p.put("sinceField", "fechaobservacion");
        p.put("chunkDays", "30");
        spec = new IndicatorSpec("temp", Theme.AMBIENTE_ENERGIA, "Temp", "C", "diaria", IndicatorSpec.Kind.SODA,
                "sbwg-7ju4", p, "v", "dia");
    }

    private static String day(String d, String v) {
        return "[{\"dia\":\"" + d + "T00:00:00.000\",\"v\":\"" + v + "\"}]";
    }

    @Test
    void windowIsSplitIntoFourContiguousThirtyDayChunks() {
        var urls = spec.chunkUrls(LocalDate.of(2026, 10, 14));
        assertThat(urls).hasSize(4);
        var decoded = urls.stream().map(u -> URLDecoder.decode(u, StandardCharsets.UTF_8)).toList();
        assertThat(decoded.get(0)).contains("fechaobservacion >= '2026-06-16T00:00:00'")
                .contains("fechaobservacion < '2026-07-16T00:00:00'");
        assertThat(decoded.get(3)).contains("fechaobservacion >= '2026-09-14T00:00:00'")
                .contains("fechaobservacion < '2026-10-15T00:00:00'"); // incluye hoy
        // contiguos: el fin de un bloque es el inicio del siguiente
        for (int i = 0; i < 3; i++) {
            String end = decoded.get(i).replaceAll(".*fechaobservacion < '([^']+)'.*", "$1");
            assertThat(decoded.get(i + 1)).contains("fechaobservacion >= '" + end + "'");
        }
    }

    @Test
    void fetchesEveryChunkAndMergesSortedWithoutDuplicates() throws Exception {
        server.expect(requestTo(containsString("2026-06-16")))
                .andRespond(withSuccess(day("2026-06-20", "20.0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("2026-07-16")))
                .andRespond(withSuccess(day("2026-07-20", "21.0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("2026-08-15")))
                .andRespond(withSuccess(day("2026-08-20", "22.0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("2026-09-14")))
                .andRespond(withSuccess("[{\"dia\":\"2026-09-20T00:00:00.000\",\"v\":\"23.0\"},"
                        + "{\"dia\":\"2026-08-20T00:00:00.000\",\"v\":\"22.0\"}]", MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec);
        server.verify();
        assertThat(s.points()).extracting(Point::date).containsExactly(
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 20));
    }

    @Test
    void oneFailingChunkFailsTheWholeSeriesSoTheOldCacheIsKept() {
        server.expect(requestTo(containsString("2026-06-16"))).andRespond(withServerError());
        server.expect(requestTo(containsString("2026-07-16")))
                .andRespond(withSuccess(day("2026-07-20", "21.0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("2026-08-15")))
                .andRespond(withSuccess(day("2026-08-20", "22.0"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("2026-09-14")))
                .andRespond(withSuccess(day("2026-09-20", "23.0"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> connector.fetch(spec)).isInstanceOf(SourceException.class);
    }

    @Test
    void specsWithoutChunkDaysStillUseASingleRequest() {
        var plain = new IndicatorSpec("t", Theme.ECONOMIA, "T", "C", "mensual", IndicatorSpec.Kind.SODA, "x",
                Map.of("$limit", "5"), "v", "mes");
        assertThat(plain.chunkUrls(LocalDate.of(2026, 10, 14))).hasSize(1);
    }
}
