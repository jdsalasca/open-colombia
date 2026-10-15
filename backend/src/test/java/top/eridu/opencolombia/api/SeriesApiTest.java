package top.eridu.opencolombia.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.refresh.RefreshService;

@SpringBootTest
@AutoConfigureMockMvc
class SeriesApiTest {
    private static final Path DATA = Path.of(System.getProperty("java.io.tmpdir"), "oc-series-test-" + System.nanoTime());

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("opencolombia.data-dir", DATA::toString);
    }

    @BeforeAll
    static void seed() {
        var store = new SeriesStore(DATA);
        List<Point> monthly = new ArrayList<>();
        for (int i = 0; i < 30; i++) monthly.add(new Point(LocalDate.of(2024, 1, 1).plusMonths(i), 3000 + i * 10));
        store.put(new Series("trm", "economia", "TRM", "COP/USD", "datos.gov.co", "u", "mensual", monthly, Instant.EPOCH));
        List<Point> annual = new ArrayList<>();
        for (int i = 0; i < 20; i++) annual.add(new Point(LocalDate.of(2005 + i, 1, 1), 10 - i * 0.2));
        store.put(new Series("desempleo", "empleo", "Desempleo", "%", "Banco Mundial", "u", "anual", annual, Instant.EPOCH));
    }

    @Autowired MockMvc mvc;
    @MockitoBean RefreshService refreshService;

    @Test
    void monthlySeriesSixMonthWindowWithRates() throws Exception {
        mvc.perform(get("/api/series/trm?range=6m")).andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("6m"))
                .andExpect(jsonPath("$.series.points.length()").value(7))
                .andExpect(jsonPath("$.rates.n").value(7))
                .andExpect(jsonPath("$.availableRanges", hasItem("3m")));
    }

    @Test
    void defaultRangeIsTodo() throws Exception {
        mvc.perform(get("/api/series/desempleo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("todo"))
                .andExpect(jsonPath("$.higherIsBetter").value(false))
                .andExpect(jsonPath("$.series.points.length()").value(20));
    }

    @Test
    void invalidRangeForPeriodicityIs400WithAvailable() throws Exception {
        mvc.perform(get("/api/series/trm?range=5a")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.availableRanges", hasItem("3m")));
        mvc.perform(get("/api/series/desempleo?range=3m")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.availableRanges", hasItem("5a")));
        mvc.perform(get("/api/series/trm?range=xx")).andExpect(status().isBadRequest());
    }

    @Test
    void annualizedRateIsOmittedForSeriesThatAreAlreadyPercentages() throws Exception {
        mvc.perform(get("/api/series/desempleo?range=10a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rates.n").value(11))
                .andExpect(jsonPath("$.rates.annualizedPct").doesNotExist())
                .andExpect(jsonPath("$.rates.changePct").doesNotExist()); // cambio relativo de una tasa: engañoso
        mvc.perform(get("/api/series/trm?range=2a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rates.annualizedPct").exists());
    }

    @Test
    void spaRoutesForwardToIndex() throws Exception {
        mvc.perform(get("/metodologia")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
        mvc.perform(get("/metodologia/trm")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("/index.html"));
    }

    @Test
    void responseCarriesFreshnessComputedFromTheLastDataDate() throws Exception {
        mvc.perform(get("/api/series/trm?range=6m")).andExpect(status().isOk())
                .andExpect(jsonPath("$.series.freshness.lastDataDate").value("2026-06-01"))
                .andExpect(jsonPath("$.series.freshness.stale").value(true))
                .andExpect(jsonPath("$.series.freshness.staleAfterDays").value(100)); // mensual: 100 días
    }

    @Test
    void dailyRangesAreRejectedForMonthlySeries() throws Exception {
        mvc.perform(get("/api/series/trm?range=7d")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.availableRanges", hasItem("6m")));
    }

    @Test
    void unknownIdIs404() throws Exception {
        mvc.perform(get("/api/series/nope")).andExpect(status().isNotFound());
    }

    @Test
    void seriesNotLoadedYetIs200WithNullRates() throws Exception {
        mvc.perform(get("/api/series/gini?range=5a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.series.points.length()").value(0))
                .andExpect(jsonPath("$.rates").doesNotExist());
    }
}
