package top.eridu.opencolombia.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.eridu.opencolombia.source.SourceException;

class LiveClientsTest {
    private static final String BANREP = "https://totoro.banrep.gov.co/estadisticas-economicas/rest/consultaDatosService/consultaMercadoCambiario";

    private MockRestServiceServer server;
    private BanrepIntradayClient banrep;
    private UsgsClient usgs;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var http = builder.build();
        banrep = new BanrepIntradayClient(http);
        usgs = new UsgsClient(http, Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));
    }

    private static String fx(String n) throws Exception {
        return new String(new ClassPathResource("fixtures/" + n).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void reducesRealIntradayTicksKeepingFirstAndLast() throws Exception {
        server.expect(requestTo(BANREP)).andRespond(withSuccess(fx("banrep-intraday.json"), MediaType.APPLICATION_JSON));
        var u = banrep.fetch().orElseThrow();
        assertThat(u.ticks().size()).isLessThanOrEqualTo(120).isGreaterThan(10);
        assertThat(u.ticks().get(0).v()).isEqualTo(u.open());
        assertThat(u.ticks().get(u.ticks().size() - 1).v()).isEqualTo(u.last());
        assertThat(u.changePct()).isCloseTo((u.last() - u.open()) / u.open() * 100, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(u.lastTickAt()).isEqualTo(u.ticks().get(u.ticks().size() - 1).t());
        assertThat(u.last()).isBetween(1000.0, 10000.0); // COP por USD plausible
    }

    @Test
    void ticksAreInChronologicalOrder() throws Exception {
        server.expect(requestTo(BANREP)).andRespond(withSuccess(fx("banrep-intraday.json"), MediaType.APPLICATION_JSON));
        var ticks = banrep.fetch().orElseThrow().ticks();
        for (int i = 1; i < ticks.size(); i++) assertThat(ticks.get(i).t()).isAfterOrEqualTo(ticks.get(i - 1).t());
    }

    @Test
    void emptyOrGarbageTicksGiveNoValueInsteadOfAnException() throws Exception {
        server.expect(requestTo(BANREP)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(banrep.fetch()).isEmpty();
        server.reset();
        server.expect(requestTo(BANREP)).andRespond(withSuccess("[[\"x\",\"y\"],[\"1791896453000\",\"3203.5\"]]", MediaType.APPLICATION_JSON));
        var one = banrep.fetch().orElseThrow();
        assertThat(one.ticks()).hasSize(1);
        assertThat(one.changePct()).isEqualTo(0.0);
    }

    @Test
    void banrepHttpErrorThrowsSourceException() {
        server.expect(requestTo(BANREP)).andRespond(withServerError());
        assertThatThrownBy(() -> banrep.fetch()).isInstanceOf(SourceException.class);
    }

    @Test
    void parsesRealQuakesNewestFirstWithCoordinates() throws Exception {
        server.expect(requestTo(startsWith("https://earthquake.usgs.gov/fdsnws/event/1/query")))
                .andExpect(requestTo(containsString("minmagnitude=2.5")))
                .andExpect(requestTo(containsString("starttime=2026-09-14")))
                .andRespond(withSuccess(fx("usgs-quakes.json"), MediaType.APPLICATION_JSON));
        var quakes = usgs.fetch();
        assertThat(quakes).isNotEmpty();
        var q = quakes.get(0);
        assertThat(q.mag()).isBetween(2.5, 9.5);
        assertThat(q.lat()).isBetween(-5.0, 14.0);
        assertThat(q.lon()).isBetween(-80.0, -66.0);
        assertThat(q.place()).isNotBlank();
        for (int i = 1; i < quakes.size(); i++) assertThat(quakes.get(i).time()).isBeforeOrEqualTo(quakes.get(i - 1).time());
    }

    @Test
    void usgsErrorsAndMalformedPayloads() {
        server.expect(requestTo(startsWith("https://earthquake.usgs.gov"))).andRespond(withServerError());
        assertThatThrownBy(() -> usgs.fetch()).isInstanceOf(SourceException.class);
        server.reset();
        server.expect(requestTo(startsWith("https://earthquake.usgs.gov")))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[{\"properties\":{\"mag\":null}}]}", MediaType.APPLICATION_JSON));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> usgs.fetch())).isNull(); // evento inválido: se omite
    }
}
