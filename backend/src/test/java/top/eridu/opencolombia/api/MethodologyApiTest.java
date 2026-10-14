package top.eridu.opencolombia.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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
class MethodologyApiTest {
    private static final Path DATA = Path.of(System.getProperty("java.io.tmpdir"), "oc-meth-test-" + System.nanoTime());

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("opencolombia.data-dir", DATA::toString);
    }

    @Autowired MockMvc mvc;
    @MockitoBean RefreshService refreshService;

    @Test
    void sodaIndicatorExposesExactQueryUrl() throws Exception {
        mvc.perform(get("/api/methodology/trm")).andExpect(status().isOk())
                .andExpect(jsonPath("$.queryUrl").value(startsWith("https://www.datos.gov.co/resource/32sa-8pi3.json?")))
                .andExpect(jsonPath("$.queryUrl").value(containsString("select")))
                .andExpect(jsonPath("$.queryUrl").value(containsString("group=mes")))
                .andExpect(jsonPath("$.seriesUrl").value("/api/series/trm"))
                .andExpect(jsonPath("$.method").isNotEmpty());
    }

    @Test
    void worldBankIndicatorQueryUrl() throws Exception {
        mvc.perform(get("/api/methodology/desempleo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.queryUrl").value(
                        "https://api.worldbank.org/v2/country/COL/indicator/SL.UEM.TOTL.ZS?format=json&per_page=200"))
                .andExpect(jsonPath("$.higherIsBetter").value(false));
    }

    @Test
    void internalFlagsNeverLeakIntoQueryUrl() throws Exception {
        mvc.perform(get("/api/methodology/homicidios-mensual")).andExpect(status().isOk())
                .andExpect(jsonPath("$.queryUrl").value(org.hamcrest.Matchers.not(containsString("dropCurrentPeriod"))));
    }

    @Test
    void unknownIdIs404AndIndexListsAll() throws Exception {
        mvc.perform(get("/api/methodology/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/api/methodology")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(14));
    }
}
