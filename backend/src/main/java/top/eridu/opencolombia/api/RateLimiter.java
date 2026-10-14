package top.eridu.opencolombia.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Límite global: una acción cada {@code window}. Reloj inyectable para pruebas. */
@Component
public class RateLimiter {
    private final Clock clock;
    private final Duration window = Duration.ofMinutes(1);
    private Instant last = Instant.MIN;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @return 0 si la acción se permite (y queda registrada); si no, segundos hasta poder reintentar. */
    public synchronized long tryAcquire() {
        Instant now = clock.instant();
        Instant next = last.equals(Instant.MIN) ? Instant.MIN : last.plus(window);
        if (now.isBefore(next)) return Math.max(1, Duration.between(now, next).toSeconds());
        last = now;
        return 0;
    }
}
