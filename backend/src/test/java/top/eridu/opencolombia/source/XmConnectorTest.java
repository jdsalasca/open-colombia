package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Theme;

class XmConnectorTest {
    private static final String URL = "https://servapibi.xm.com.co/hourly";

    private MockRestServiceServer server;
    private XmConnector connector;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        connector = new XmConnector(builder.build(), Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));
    }

    private static String fx(String n) throws Exception {
        return new String(new ClassPathResource("fixtures/" + n).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static IndicatorSpec spec(String id, String metric, String agg, String scale, int windowDays) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("agg", agg);
        p.put("scale", scale);
        p.put("entity", "Sistema");
        p.put("windowDays", String.valueOf(windowDays));
        return new IndicatorSpec(id, Theme.AMBIENTE_ENERGIA, id, "u", "diaria", IndicatorSpec.Kind.XM, metric, p,
                null, null);
    }

    @Test
    void averagesTwentyFourHoursPerDay() throws Exception {
        server.expect(method(HttpMethod.POST)).andExpect(requestTo(URL))
                .andExpect(content().string(containsString("\"MetricId\":\"PrecBolsNaci\"")))
                .andExpect(content().string(containsString("\"Entity\":\"Sistema\"")))
                .andRespond(withSuccess(fx("xm-PrecBolsNaci.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec("precio", "PrecBolsNaci", "avg", "1", 20));
        assertThat(s.points()).hasSize(2);
        assertThat(s.points().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(s.points().get(0).value()).isBetween(300.0, 3000.0); // COP/kWh plausible
        assertThat(s.source()).isEqualTo("XM");
        assertThat(s.periodicity()).isEqualTo("diaria");
    }

    @Test
    void sumsGenerationAndAppliesTheScale() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(fx("xm-Gene.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec("generacion", "Gene", "sum", "1000000", 20));
        Point p = s.points().get(0);
        assertThat(p.value()).isBetween(150.0, 350.0); // GWh/día: escala de generación de Colombia
    }

    @Test
    void dayWithTooFewValidHoursIsDroppedNotAveragedWithGaps() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(fx("xm-sparse.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec("precio", "PrecBolsNaci", "avg", "1", 20));
        // día 1: 10 horas -> fuera; día 2: 22 horas válidas (>= 20) -> se promedia
        assertThat(s.points()).extracting(Point::date).containsExactly(LocalDate.of(2026, 10, 11));
    }

    @Test
    void aSumRequiresAllTwentyFourHours() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(fx("xm-sparse.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec("demanda", "PrecBolsNaci", "sum", "1000000", 20));
        assertThat(s.points()).isEmpty(); // 22 horas no bastan para una suma diaria
    }

    @Test
    void windowOfOneHundredTwentyDaysUsesFourRequestsOfAtMostThirtyOneDays() throws Exception {
        for (int i = 0; i < 4; i++) {
            server.expect(requestTo(URL)).andRespond(withSuccess(fx("xm-empty.json"), MediaType.APPLICATION_JSON));
        }
        var s = connector.fetch(spec("precio", "PrecBolsNaci", "avg", "1", 120));
        server.verify();
        assertThat(s.points()).isEmpty();
        var starts = connector.windowsFor(LocalDate.of(2026, 10, 14), 120);
        assertThat(starts).hasSize(4);
        assertThat(starts.get(0).from()).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(starts.get(3).toExclusive()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(starts).allSatisfy(w -> assertThat(w.toExclusive().toEpochDay() - w.from().toEpochDay())
                .isLessThanOrEqualTo(31));
    }

    @Test
    void httpErrorThrowsSourceException() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> connector.fetch(spec("precio", "PrecBolsNaci", "avg", "1", 20)))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void anUnreadableBodyThrowsSourceException() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>mantenimiento</html>", MediaType.TEXT_HTML));
        assertThatThrownBy(() -> connector.fetch(spec("precio", "PrecBolsNaci", "avg", "1", 20)))
                .isInstanceOf(SourceException.class);
    }

    @Test
    void methodologyExposesTheEndpointAndTheJsonBody() {
        var s = spec("precio", "PrecBolsNaci", "avg", "1", 120);
        assertThat(s.queryUrl(LocalDate.of(2026, 10, 14))).isEqualTo(URL);
        assertThat(s.queryBody(LocalDate.of(2026, 10, 14)))
                .contains("\"MetricId\":\"PrecBolsNaci\"")
                .contains("\"StartDate\":\"2026-06-16\"")
                .contains("\"EndDate\":\"2026-07-17\""); // ventana de 31 días
    }
}
