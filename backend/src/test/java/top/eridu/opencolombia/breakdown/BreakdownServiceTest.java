package top.eridu.opencolombia.breakdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.eridu.opencolombia.analytics.Range;
import top.eridu.opencolombia.source.SourceException;

class BreakdownServiceTest {
    private static final String BASE = "https://www.datos.gov.co/resource/m8fd-ahd9.json";

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-13T00:00:00Z");

        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    private MockRestServiceServer server;
    private BreakdownService service;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock();
        service = new BreakdownService(builder.build(), clock);
    }

    private static String fx(String n) throws Exception {
        return new String(new ClassPathResource("fixtures/" + n).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static String rows(int n) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"k\":\"DEPTO ").append(i).append("\",\"v\":\"").append(100 - i).append("\"}");
        }
        return sb.append(']').toString();
    }

    @Test
    void sexoFromRealFixtureSharesSumToOne() throws Exception {
        server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(fx("breakdown-sexo.json"), MediaType.APPLICATION_JSON));
        var b = service.get("sexo", Range.Y1);
        assertThat(b.items()).hasSize(3);
        assertThat(b.items().get(0).value()).isGreaterThan(b.items().get(1).value());
        assertThat(b.items().stream().mapToDouble(Breakdown.Item::share).sum()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(b.total()).isEqualTo(b.items().stream().mapToDouble(Breakdown.Item::value).sum());
        assertThat(b.from()).isEqualTo(LocalDate.of(2025, 10, 13));
        assertThat(b.queryUrl()).startsWith(BASE).contains("fecha_hecho");
    }

    @Test
    void departamentoKeepsTopEightPlusOtros() throws Exception {
        server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(rows(11), MediaType.APPLICATION_JSON));
        var b = service.get("departamento", Range.Y1);
        assertThat(b.items()).hasSize(9);
        assertThat(b.items().get(8).label()).isEqualTo("OTROS");
        assertThat(b.items().get(8).value()).isEqualTo(92 + 91 + 90);
    }

    @Test
    void pieDimensionKeepsTopSixPlusOtros() throws Exception {
        server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess(rows(9), MediaType.APPLICATION_JSON));
        assertThat(service.get("arma", Range.Y1).items()).hasSize(7);
    }

    @Test
    void singleCategoryAndEmptyAreSafe() throws Exception {
        server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess("[{\"k\":\"URBANA\",\"v\":\"5\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        var one = service.get("zona", Range.M3);
        assertThat(one.items()).hasSize(1);
        assertThat(one.items().get(0).share()).isEqualTo(1.0);

        var none = service.get("zona", Range.M6);
        assertThat(none.items()).isEmpty();
        assertThat(none.total()).isEqualTo(0.0);
    }

    @Test
    void cachesForAnHourThenRefetches() throws Exception {
        server.expect(once(), requestTo(startsWith(BASE))).andRespond(withSuccess(rows(2), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(startsWith(BASE))).andRespond(withSuccess(rows(2), MediaType.APPLICATION_JSON));
        service.get("zona", Range.Y1);
        service.get("zona", Range.Y1); // desde caché: no consume la segunda expectativa
        clock.now = clock.now.plus(Duration.ofMinutes(61));
        service.get("zona", Range.Y1); // caché vencida: consume la segunda
        server.verify();
    }

    @Test
    void upstreamErrorThrowsSourceException() {
        server.expect(requestTo(startsWith(BASE))).andRespond(withServerError());
        assertThatThrownBy(() -> service.get("sexo", Range.Y1)).isInstanceOf(SourceException.class);
    }

    @Test
    void unknownDimensionIsRejected() {
        assertThatThrownBy(() -> service.get("nope", Range.Y1)).isInstanceOf(IllegalArgumentException.class);
    }
}
