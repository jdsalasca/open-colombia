package top.eridu.opencolombia.api;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.refresh.RefreshService;

@RestController
public class RefreshController {
    private final RefreshService service;
    private final RateLimiter limiter;

    public RefreshController(RefreshService service, RateLimiter limiter) {
        this.service = service;
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
        return ResponseEntity.ok(service.refreshAll());
    }
}
