package top.eridu.opencolombia.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.insight.InsightService;
import top.eridu.opencolombia.model.Insight;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;
import top.eridu.opencolombia.source.IndicatorSpec;

@RestController
public class IndicatorController {
    public record ThemeView(String id, String name, List<Series> series) {}

    public record IndicatorsResponse(List<ThemeView> themes, Instant updatedAt) {}

    private final SeriesStore store;
    private final InsightService insights;
    private final List<IndicatorSpec> specs;

    public IndicatorController(SeriesStore store, InsightService insights, List<IndicatorSpec> specs) {
        this.store = store;
        this.insights = insights;
        this.specs = specs;
    }

    /** Todos los indicadores del catálogo: los que no cargaron salen vacíos, no desaparecen. */
    private List<Series> merged() {
        Map<String, Series> cached = store.all().stream()
                .collect(Collectors.toMap(Series::id, Function.identity(), (a, b) -> b));
        return specs.stream().map(sp -> cached.getOrDefault(sp.id(), sp.placeholder())).toList();
    }

    private ThemeView view(Theme t, List<Series> all) {
        return new ThemeView(t.id(), t.label(),
                all.stream().filter(s -> s.theme().equals(t.id())).toList());
    }

    @GetMapping("/api/indicators")
    public IndicatorsResponse indicators() {
        List<Series> all = merged();
        Instant updated = all.stream().map(Series::fetchedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new IndicatorsResponse(Arrays.stream(Theme.values()).map(t -> view(t, all)).toList(), updated);
    }

    @GetMapping("/api/themes/{id}")
    public ResponseEntity<ThemeView> theme(@PathVariable String id) {
        return Theme.byId(id).map(t -> ResponseEntity.ok(view(t, merged())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/insights")
    public List<Insight> insights() {
        return insights.insights();
    }
}
