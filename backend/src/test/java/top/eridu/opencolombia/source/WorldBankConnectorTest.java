package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Theme;

class WorldBankConnectorTest {
    private static final String URL =
            "https://api.worldbank.org/v2/country/COL/indicator/SL.UEM.TOTL.ZS?format=json&per_page=200";

    private MockRestServiceServer server;
    private WorldBankConnector connector;
    private final IndicatorSpec spec = new IndicatorSpec("desempleo", Theme.EMPLEO, "Desempleo", "%", "anual",
            IndicatorSpec.Kind.WORLD_BANK, "SL.UEM.TOTL.ZS", Map.of(), null, null);

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        connector = new WorldBankConnector(builder.build(),
                Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC));
    }

    private String fx(String n) throws Exception {
        return new String(new ClassPathResource("fixtures/" + n).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void parsesAndSortsAscending() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(fx("worldbank-ok.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec);
        assertThat(s.points()).isNotEmpty().isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
        assertThat(s.fetchedAt()).isEqualTo(Instant.parse("2026-10-13T00:00:00Z"));
        assertThat(s.source()).isEqualTo("Banco Mundial");
        assertThat(s.sourceUrl()).contains("SL.UEM.TOTL.ZS");
    }

    @Test
    void dropsNullEmptyAndDotValues() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(fx("worldbank-nulls.json"), MediaType.APPLICATION_JSON));
        var s = connector.fetch(spec);
        assertThat(s.points()).extracting(Point::value).containsExactly(6.1, 7.5);
    }

    @Test
    void errorPayloadIsEmptySeries() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(fx("worldbank-empty.json"), MediaType.APPLICATION_JSON));
        assertThat(connector.fetch(spec).points()).isEmpty();
    }

    @Test
    void serverErrorThrowsSourceException() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> connector.fetch(spec)).isInstanceOf(SourceException.class);
    }
}
