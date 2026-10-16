package top.eridu.opencolombia.refresh;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.IndicatorSpec.RefreshClass;
import top.eridu.opencolombia.source.SourceConnector;
import top.eridu.opencolombia.source.SourceException;

@Service
public class RefreshService {
    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);
    /** Lo único que se le dice al cliente de un fallo: el detalle (clases, rutas) solo va al log. */
    static final String GENERIC_ERROR = "La fuente no respondió.";

    private final List<SourceConnector> connectors;
    private final SeriesStore store;
    private final List<IndicatorSpec> specs;
    private final java.time.Clock clock;
    private CompletableFuture<RefreshReport> inFlight; // protegido por this

    @org.springframework.beans.factory.annotation.Autowired
    public RefreshService(List<SourceConnector> connectors, SeriesStore store, List<IndicatorSpec> specs,
                          java.time.Clock clock) {
        this.connectors = connectors;
        this.store = store;
        this.specs = specs;
        this.clock = clock;
    }

    public RefreshService(List<SourceConnector> connectors, SeriesStore store, List<IndicatorSpec> specs) {
        this(connectors, store, specs, java.time.Clock.systemUTC());
    }

    /** Re-consulta todas las fuentes en paralelo. Un fallo nunca borra datos previos. */
    public RefreshReport refreshAll() {
        return shared(() -> run(specs));
    }

    /** Re-consulta solo los indicadores de una clase de refresco. */
    public RefreshReport refreshClass(RefreshClass c) {
        return shared(() -> run(specs.stream().filter(s -> s.refresh() == c).toList()));
    }

    /** ¿Algún indicador falta o está vencido según la fecha real de su último dato? */
    public boolean anyStale(LocalDate today) {
        for (IndicatorSpec spec : specs) {
            Series s = store.get(spec.id()).orElse(null);
            if (s == null || spec.freshen(s, today).freshness().stale()) return true;
        }
        return false;
    }

    /**
     * Una sola ejecución en curso: si llega otra petición mientras corre (botón + scheduler), comparte su
     * resultado en vez de consultar de nuevo las fuentes.
     */
    private RefreshReport shared(Supplier<RefreshReport> work) {
        CompletableFuture<RefreshReport> f;
        boolean owner = false;
        synchronized (this) {
            if (inFlight == null) {
                inFlight = new CompletableFuture<>();
                owner = true;
            }
            f = inFlight;
        }
        if (owner) {
            try {
                f.complete(work.get());
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
            } finally {
                synchronized (this) {
                    inFlight = null;
                }
            }
        }
        return f.join();
    }

    private RefreshReport run(List<IndicatorSpec> targets) {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SourceResult>> futures = new ArrayList<>();
            for (IndicatorSpec spec : targets) futures.add(pool.submit(() -> refreshOne(spec)));
            List<SourceResult> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (InterruptedException | ExecutionException e) {
                    log.warn("Refresco interrumpido para {}", targets.get(i).id(), e);
                    results.add(new SourceResult(targets.get(i).id(), false, GENERIC_ERROR));
                }
            }
            return new RefreshReport(Instant.now(), results);
        }
    }

    /** Une lo guardado (dentro de la ventana) con lo recién pedido; lo nuevo pisa lo viejo en la misma fecha. */
    private static Series merge(Series cached, Series fresh, LocalDate windowStart) {
        java.util.Map<LocalDate, top.eridu.opencolombia.model.Point> byDate = new java.util.TreeMap<>();
        cached.points().stream().filter(p -> !p.date().isBefore(windowStart)).forEach(p -> byDate.put(p.date(), p));
        fresh.points().forEach(p -> byDate.put(p.date(), p));
        return fresh.withPoints(new ArrayList<>(byDate.values()));
    }

    private SourceResult refreshOne(IndicatorSpec spec) {
        SourceConnector c = connectors.stream().filter(x -> x.supports(spec)).findFirst().orElse(null);
        if (c == null) return new SourceResult(spec.id(), false, "Sin conector para esta fuente.");
        // series con ventana móvil y caché reciente: se pide solo lo nuevo y se fusiona con lo guardado
        LocalDate today = LocalDate.now(clock);
        Series cached = store.get(spec.id()).orElse(null);
        IndicatorSpec effective = spec.incremental(cached, today).orElse(spec);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Series s = c.fetch(effective);
                if (s.points().isEmpty()) return new SourceResult(spec.id(), false, "La fuente no devolvió datos.");
                if (effective != spec) s = merge(cached, s, today.minusDays(spec.rollingWindowDays()));
                store.put(s);
                return new SourceResult(spec.id(), true, null);
            } catch (SourceException | RuntimeException e) {
                last = e;
            }
        }
        log.warn("Fuente falló: {}", spec.id(), last);
        return new SourceResult(spec.id(), false, GENERIC_ERROR);
    }
}
