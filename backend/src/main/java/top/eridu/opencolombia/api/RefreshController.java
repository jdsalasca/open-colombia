package top.eridu.opencolombia.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.live.LiveService;
import top.eridu.opencolombia.live.LiveSnapshot;
import top.eridu.opencolombia.refresh.RefreshReport;
import top.eridu.opencolombia.refresh.RefreshService;
import top.eridu.opencolombia.refresh.SourceResult;

@RestController
public class RefreshController {
    private final RefreshService service;
    private final LiveService live;
    private final RateLimiter limiter;

    public RefreshController(RefreshService service, LiveService live, RateLimiter limiter) {
        this.service = service;
        this.live = live;
        this.limiter = limiter;
    }

    @PostMapping("/api/refresh")
    public ResponseEntity<?> refresh() {
        long wait = limiter.tryAcquire();
        if (wait > 0) {
            return ResponseEntity.status(429).header(HttpHeaders.RETRY_AFTER, String.valueOf(wait))
                    .body(Map.of("error", "Actualización reciente; intenta de nuevo en unos segundos.",
                            "retryAfter", wait));
        }
        RefreshReport report = service.refreshAll();
        LiveSnapshot snap = live.refresh();
        List<SourceResult> results = new ArrayList<>(report.results());
        results.add(result("usd-cop-intradia", "banrep", snap));
        results.add(result("sismos", "usgs", snap));
        // si nada se pudo actualizar, el siguiente intento no debe esperar un minuto
        if (!results.isEmpty() && results.stream().noneMatch(SourceResult::ok)) limiter.release();
        return ResponseEntity.ok(new RefreshReport(report.at() != null ? report.at() : Instant.now(), results));
    }

    private static SourceResult result(String id, String errorKey, LiveSnapshot snap) {
        String err = snap.errors().get(errorKey);
        return new SourceResult(id, err == null, err);
    }
}
