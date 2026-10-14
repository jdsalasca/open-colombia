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
    void idsUniqueAndRefsPresent() {
        assertThat(specs).extracting(IndicatorSpec::id).doesNotHaveDuplicates();
        assertThat(specs).allSatisfy(s -> assertThat(s.ref()).isNotBlank());
    }
}
