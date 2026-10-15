package top.eridu.opencolombia.live;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import top.eridu.opencolombia.refresh.RefreshService;

@SpringBootTest
@AutoConfigureMockMvc
class LiveApiTest {
    private static final Path DATA = Path.of(System.getProperty("java.io.tmpdir"), "oc-live-test-" + System.nanoTime());

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("opencolombia.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @MockitoBean RefreshService refreshService;
    @MockitoBean BanrepIntradayClient banrep;
    @MockitoBean UsgsClient usgs;

    @Test
    void emptyCacheIs200WithNullUsdAndNoQuakes() throws Exception {
        mvc.perform(get("/api/live")).andExpect(status().isOk())
                .andExpect(jsonPath("$.usdCop").doesNotExist())
                .andExpect(jsonPath("$.quakes.length()").value(0));
    }
}
