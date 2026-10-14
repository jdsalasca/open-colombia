package top.eridu.opencolombia.breakdown;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.eridu.opencolombia.analytics.Range;
import top.eridu.opencolombia.refresh.RefreshService;
import top.eridu.opencolombia.source.SourceException;

@SpringBootTest
@AutoConfigureMockMvc
class BreakdownApiTest {
    private static final Path DATA = Path.of(System.getProperty("java.io.tmpdir"), "oc-bd-test-" + System.nanoTime());

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("opencolombia.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @MockitoBean RefreshService refreshService;
    @MockitoBean BreakdownService service;

    @Test
    void okReturnsBreakdown() throws Exception {
        when(service.get(eq("sexo"), eq(Range.Y1))).thenReturn(new Breakdown("sexo", "1a", LocalDate.of(2025, 10, 13),
                10, List.of(new Breakdown.Item("MASCULINO", 10, 1.0)), "datos.gov.co", "https://x"));
        mvc.perform(get("/api/breakdowns/homicidios?dim=sexo&range=1a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].label").value("MASCULINO"));
    }

    @Test
    void invalidDimOrRangeIs400() throws Exception {
        mvc.perform(get("/api/breakdowns/homicidios?dim=nope&range=1a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/breakdowns/homicidios?dim=sexo&range=5a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/breakdowns/homicidios?dim=sexo&range=todo")).andExpect(status().isBadRequest());
    }

    @Test
    void upstreamFailureIs502() throws Exception {
        when(service.get(any(), any())).thenThrow(new SourceException("caído"));
        mvc.perform(get("/api/breakdowns/homicidios?dim=zona&range=3m")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
