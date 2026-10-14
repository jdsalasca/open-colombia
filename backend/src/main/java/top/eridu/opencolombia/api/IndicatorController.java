package top.eridu.opencolombia.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.insight.InsightService;
import top.eridu.opencolombia.model.Insight;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.model.Theme;

@RestController
public class IndicatorController {
    public record ThemeView(String id, String name, List<Series> series) {}

    public record IndicatorsResponse(List<ThemeView> themes, Instant updatedAt) {}

    private final SeriesStore store;
    private final InsightService insights;

    public IndicatorController(SeriesStore store, InsightService insights) {
        this.store = store;
        this.insights = insights;
    }

    private ThemeView view(Theme t, List<Series> all) {
        return new ThemeView(t.id(), t.label(),
                all.stream().filter(s -> s.theme().equals(t.id())).toList());
    }

    @GetMapping("/api/indicators")
    public IndicatorsResponse indicators() {
        List<Series> all = store.all();
        Instant updated = all.stream().map(Series::fetchedAt).max(Comparator.naturalOrder()).orElse(null);
        return new IndicatorsResponse(Arrays.stream(Theme.values()).map(t -> view(t, all)).toList(), updated);
    }

    @GetMapping("/api/themes/{id}")
    public ResponseEntity<ThemeView> theme(@PathVariable String id) {
        return Theme.byId(id).map(t -> ResponseEntity.ok(view(t, store.all())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/insights")
    public List<Insight> insights() {
        return insights.insights();
    }
}
