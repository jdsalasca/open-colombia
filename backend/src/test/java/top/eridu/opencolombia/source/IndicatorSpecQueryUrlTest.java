package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import top.eridu.opencolombia.model.Theme;

class IndicatorSpecQueryUrlTest {
    private static IndicatorSpec spec(Map<String, String> params) {
        return new IndicatorSpec("t", Theme.AMBIENTE_ENERGIA, "T", "C", "diaria", IndicatorSpec.Kind.SODA,
                "sbwg-7ju4", params, "v", "dia");
    }

    private static String decoded(String url) {
        return URLDecoder.decode(url, StandardCharsets.UTF_8);
    }

    @Test
    void rollingWindowBecomesAWhereClauseAndFlagsNeverTravel() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$select", "date_trunc_ymd(fechaobservacion) as dia, avg(valorobservado) as v");
        p.put("sinceDaysAgo", "120");
        p.put("sinceField", "fechaobservacion");
        p.put("divideBy", "n");
        p.put("dropCurrentDay", "true");
        String url = decoded(spec(p).queryUrl(LocalDate.of(2026, 10, 14)));
        assertThat(url).contains("fechaobservacion >= '2026-06-16T00:00:00'");
        assertThat(url).doesNotContain("sinceDaysAgo").doesNotContain("divideBy").doesNotContain("dropCurrentDay");
    }

    @Test
    void windowIsCombinedWithAnExistingWhereUsingAnd() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$where", "valorobservado between -5 and 45");
        p.put("sinceDaysAgo", "30");
        p.put("sinceField", "fechaobservacion");
        String url = decoded(spec(p).queryUrl(LocalDate.of(2026, 10, 14)));
        assertThat(url).contains("(valorobservado between -5 and 45) AND fechaobservacion >= '2026-09-14T00:00:00'");
    }

    @Test
    void specsWithoutAWindowKeepTheirQueryUnchanged() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("$limit", "5000");
        assertThat(decoded(spec(p).queryUrl(LocalDate.of(2026, 10, 14))))
                .isEqualTo("https://www.datos.gov.co/resource/sbwg-7ju4.json?$limit=5000");
    }
}
