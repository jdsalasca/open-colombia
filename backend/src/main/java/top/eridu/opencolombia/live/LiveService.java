package top.eridu.opencolombia.live;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.eridu.opencolombia.source.SourceException;

/** Refresca las fuentes en vivo de forma independiente: una caída no borra ni bloquea a la otra. */
@Service
public class LiveService {
    private static final Logger log = LoggerFactory.getLogger(LiveService.class);

    private final UsdCopSource usd;
    private final QuakeSource quakes;
    private final LiveStore store;
    private final Clock clock;
    private volatile LiveSnapshot current;

    public LiveService(UsdCopSource usd, QuakeSource quakes, LiveStore store, Clock clock) {
        this.usd = usd;
        this.quakes = quakes;
        this.store = store;
        this.clock = clock;
    }

    /** Último snapshot (memoria o disco); nunca null. */
    public LiveSnapshot get() {
        LiveSnapshot c = current;
        if (c == null) {
            c = store.get().orElse(LiveSnapshot.empty());
            current = c;
        }
        return c;
    }

    public synchronized LiveSnapshot refresh() {
        LiveSnapshot prev = get();
        Map<String, String> errors = new LinkedHashMap<>();
        boolean anyOk = false;

        UsdCop usdCop = prev.usdCop();
        try {
            Optional<UsdCop> u = usd.fetch();
            usdCop = u.orElse(null); // sin cotizaciones hoy: no es un error, pero tampoco hay dato «en vivo»
            anyOk = true;
        } catch (SourceException e) {
            log.warn("TRM intradía no disponible", e);
            errors.put("banrep", "La fuente no respondió.");
        }

        List<Quake> qk = prev.quakes();
        try {
            qk = quakes.fetch();
            anyOk = true;
        } catch (SourceException e) {
            log.warn("Sismos no disponibles", e);
            errors.put("usgs", "La fuente no respondió.");
        }

        Instant fetchedAt = anyOk ? clock.instant() : prev.fetchedAt();
        LiveSnapshot next = new LiveSnapshot(usdCop, qk, fetchedAt, errors);
        store.put(next);
        current = next;
        return next;
    }
}
