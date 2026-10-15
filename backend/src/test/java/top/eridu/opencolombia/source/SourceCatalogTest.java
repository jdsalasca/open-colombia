package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import top.eridu.opencolombia.model.Theme;

class SourceCatalogTest {
    private final List<IndicatorSpec> specs = SourceCatalog.specs();

    @Test
    void everyThemeHasAtLeastTwoIndicators() {
        var byTheme = specs.stream().collect(Collectors.groupingBy(IndicatorSpec::theme, Collectors.counting()));
        Arrays.stream(Theme.values()).forEach(t -> assertThat(byTheme.getOrDefault(t, 0L)).isGreaterThanOrEqualTo(2));
    }

    @Test
    void v3PoliceCrimeSeriesAreMonthlyAndLowerIsBetter() {
        for (String id : List.of("hurto-personas", "extorsion", "secuestro", "delitos-informaticos",
                "violencia-intrafamiliar")) {
            var s = specs.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            assertThat(s.theme()).isEqualTo(Theme.SEGURIDAD);
            assertThat(s.periodicity()).isEqualTo("mensual");
            assertThat(s.higherIsBetter()).isFalse();
            assertThat(s.params()).containsEntry("dropCurrentPeriod", "true");
            assertThat(s.params()).containsEntry("dropLastPeriods", "1"); // el último mes llega incompleto
            assertThat(s.method()).contains("consolida");
            assertThat(s.method()).isNotBlank();
        }
    }

    @Test
    void v3IdeamSeriesAreDailyWithRollingWindowAndSevenDayExpiry() {
        for (String id : List.of("temperatura-nacional", "precipitacion-nacional")) {
            var s = specs.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
            assertThat(s.theme()).isEqualTo(Theme.AMBIENTE_ENERGIA);
            assertThat(s.periodicity()).isEqualTo("diaria");
            assertThat(s.params()).containsKeys("sinceDaysAgo", "sinceField", "dropCurrentDay");
            assertThat(s.staleAfterDays()).isEqualTo(7);
        }
        var rain = specs.stream().filter(x -> x.id().equals("precipitacion-nacional")).findFirst().orElseThrow();
        assertThat(rain.params()).containsEntry("divideBy", "n");
    }

    @Test
    void idsUniqueAndRefsPresent() {
        assertThat(specs).extracting(IndicatorSpec::id).doesNotHaveDuplicates();
        assertThat(specs).allSatisfy(s -> assertThat(s.ref()).isNotBlank());
    }
}
