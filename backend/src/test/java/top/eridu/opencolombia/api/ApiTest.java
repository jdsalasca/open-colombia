package top.eridu.opencolombia.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.Mockito;
import top.eridu.opencolombia.refresh.RefreshReport;
import top.eridu.opencolombia.refresh.RefreshService;
import java.time.Instant;
import java.util.List;

@SpringBootTest(properties = {"spring.main.lazy-initialization=false"})
@AutoConfigureMockMvc
class ApiTest {
    private static final Path DATA = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
            "oc-api-test-" + System.nanoTime());

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("opencolombia.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @MockitoBean RefreshService refreshService;

    @Test
    void indicatorsWithEmptyStoreIs200WithThemesAndNoSeries() throws Exception {
        mvc.perform(get("/api/indicators")).andExpect(status().isOk())
                .andExpect(jsonPath("$.themes.length()").value(6))
                .andExpect(jsonPath("$.themes[0].series.length()").value(0));
    }

    @Test
    void unknownThemeIs404() throws Exception {
        mvc.perform(get("/api/themes/nope")).andExpect(status().isNotFound());
    }

    @Test
    void knownThemeIs200() throws Exception {
        mvc.perform(get("/api/themes/economia")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("economia"));
    }

    @Test
    void healthIs200() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void insightsIs200() throws Exception {
        mvc.perform(get("/api/insights")).andExpect(status().isOk());
    }

    @Test
    void secondRefreshWithinAMinuteIs429() throws Exception {
        Mockito.when(refreshService.refreshAll()).thenReturn(new RefreshReport(Instant.now(), List.of()));
        mvc.perform(post("/api/refresh")).andExpect(status().isOk());
        mvc.perform(post("/api/refresh")).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
