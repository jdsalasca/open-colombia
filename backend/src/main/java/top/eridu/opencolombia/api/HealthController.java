package top.eridu.opencolombia.api;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Series;

@RestController
public class HealthController {
    public record Health(String status, int series, Instant updatedAt) {}

    private final SeriesStore store;

    public HealthController(SeriesStore store) {
        this.store = store;
    }

    @GetMapping("/api/health")
    public Health health() {
        List<Series> all = store.all();
        return new Health("ok", all.size(),
                all.stream().map(Series::fetchedAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
    }
}
