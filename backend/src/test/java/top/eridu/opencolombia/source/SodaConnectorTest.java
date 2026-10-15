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
    void dailyRatioDividesSumByStationsAndSkipsZeroDivisors() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", "date_trunc_ymd(fechaobservacion) as dia, sum(valorobservado) as s, count(distinct codigoestacion) as n");
        p.put("divideBy", "n");
        p.put("dropCurrentDay", "true");
        var rain = new IndicatorSpec("lluvia", Theme.AMBIENTE_ENERGIA, "Lluvia", "mm", "diaria",
                IndicatorSpec.Kind.SODA, "s54a-sgyg", p, "s", "dia");
        server.expect(requestTo(startsWith("https://www.datos.gov.co/resource/s54a-sgyg.json")))
                .andRespond(withSuccess(fx("soda-rain.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(rain);
        // 2026-10-10 tiene n=0 (se omite) y 2026-10-13 es «hoy» según el reloj fijo (se descarta por incompleto)
        assertThat(s.points()).extracting(Point::date)
                .containsExactly(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 12));
        assertThat(s.points().get(1).value()).isCloseTo(6293.424 / 476, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void dropsTheLastPublishedPeriodsWhenTheSourceConsolidatesWithLag() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dropLastPeriods", "1");
        var crime = new IndicatorSpec("hurto", Theme.SEGURIDAD, "Hurto", "casos/mes", "mensual",
                IndicatorSpec.Kind.SODA, "4rxi-8m8d", p, "v", "mes");
        server.expect(requestTo(startsWith("https://www.datos.gov.co/resource/4rxi-8m8d.json")))
                .andRespond(withSuccess("[{\"mes\":\"2026-06-01T00:00:00.000\",\"v\":\"22725\"},"
                        + "{\"mes\":\"2026-07-01T00:00:00.000\",\"v\":\"22591\"},"
                        + "{\"mes\":\"2026-08-01T00:00:00.000\",\"v\":\"18686\"}]", MediaType.APPLICATION_JSON));
        assertThat(connector.fetch(crime).points()).extracting(Point::date)
                .containsExactly(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
    }

    @Test
    void lagFlagNeverEmptiesASeriesWithASinglePoint() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dropLastPeriods", "2");
        var crime = new IndicatorSpec("hurto", Theme.SEGURIDAD, "Hurto", "casos/mes", "mensual",
                IndicatorSpec.Kind.SODA, "4rxi-8m8d", p, "v", "mes");
        server.expect(requestTo(startsWith("https://www.datos.gov.co/resource/4rxi-8m8d.json")))
                .andRespond(withSuccess("[{\"mes\":\"2026-08-01T00:00:00.000\",\"v\":\"100\"}]",
                        MediaType.APPLICATION_JSON));
        // con menos puntos que los que se descartarían se conserva todo: mejor un dato dudoso que ninguno
        assertThat(connector.fetch(crime).points()).hasSize(1);
    }

    @Test
    void dropsTheCurrentDayWhenFlagged() throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("dropCurrentDay", "true");
        var temp = new IndicatorSpec("temp", Theme.AMBIENTE_ENERGIA, "Temperatura", "C", "diaria",
                IndicatorSpec.Kind.SODA, "sbwg-7ju4", p, "v", "dia");
        server.expect(requestTo(startsWith("https://www.datos.gov.co/resource/sbwg-7ju4.json")))
                .andRespond(withSuccess("[{\"dia\":\"2026-10-12T00:00:00.000\",\"v\":\"21.3\"},"
                        + "{\"dia\":\"2026-10-13T00:00:00.000\",\"v\":\"30.0\"}]", MediaType.APPLICATION_JSON));
        assertThat(connector.fetch(temp).points()).extracting(Point::date)
                .containsExactly(LocalDate.of(2026, 10, 12)); // 13 = hoy según el reloj fijo
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
