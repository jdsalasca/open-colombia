package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Theme;

class SodaConnectorTest {
    private static final String BASE = "https://www.datos.gov.co/resource/32sa-8pi3.json";

    private MockRestServiceServer server;
    private SodaConnector connector;
    private IndicatorSpec spec;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        connector = new SodaConnector(builder.build(),
                Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", "date_trunc_ym(vigenciadesde) as mes, avg(valor) as v");
        p.put("$limit", "5000");
        spec = new IndicatorSpec("trm", Theme.ECONOMIA, "TRM", "COP/USD", "mensual", IndicatorSpec.Kind.SODA,
                "32sa-8pi3", p, "v", "mes");
    }

    private String fx(String n) throws Exception {
        return new String(new ClassPathResource("fixtures/" + n).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void parsesSortsAndDropsRowsWithoutValue() throws Exception {
        server.expect(requestTo(startsWith(BASE)))
                .andRespond(withSuccess(fx("soda-ok.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec);
        assertThat(s.points()).extracting(Point::date)
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        assertThat(s.points().get(1).value()).isCloseTo(3126.7757894736842, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(s.source()).isEqualTo("datos.gov.co");
    }

    @Test
    void serverErrorThrowsSourceException() {
        server.expect(requestTo(startsWith(BASE))).andRespond(withServerError());
        assertThatThrownBy(() -> connector.fetch(spec)).isInstanceOf(SourceException.class);
    }

    @Test
    void emptyArrayIsEmptySeries() throws Exception {
        server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(connector.fetch(spec).points()).isEmpty();
    }

    @Test
    void dropsCurrentPartialMonthWhenFlagged() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", "date_trunc_ym(fecha_hecho) as mes, sum(cantidad) as v");
        p.put("dropCurrentPeriod", "true");
        var flagged = new IndicatorSpec("hom", Theme.SEGURIDAD, "Homicidios", "casos/mes", "mensual",
                IndicatorSpec.Kind.SODA, "m8fd-ahd9", p, "v", "mes");
        server.expect(requestTo(startsWith("https://www.datos.gov.co/resource/m8fd-ahd9.json")))
                .andRespond(withSuccess("[{\"mes\":\"2026-09-01T00:00:00.000\",\"v\":\"1100\"},"
                        + "{\"mes\":\"2026-10-01T00:00:00.000\",\"v\":\"300\"}]", MediaType.APPLICATION_JSON));
        assertThat(connector.fetch(flagged).points()).extracting(Point::date)
                .containsExactly(LocalDate.of(2026, 9, 1)); // reloj fijo: 2026-10-13
    }
}
