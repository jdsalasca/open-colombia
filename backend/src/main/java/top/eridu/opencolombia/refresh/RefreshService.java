package top.eridu.opencolombia.refresh;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.SourceConnector;
import top.eridu.opencolombia.source.SourceException;

@Service
public class RefreshService {
    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);

    private final List<SourceConnector> connectors;
    private final SeriesStore store;
    private final List<IndicatorSpec> specs;

    public RefreshService(List<SourceConnector> connectors, SeriesStore store, List<IndicatorSpec> specs) {
        this.connectors = connectors;
        this.store = store;
        this.specs = specs;
    }

    /** Re-consulta todas las fuentes en paralelo. Un fallo nunca borra datos previos. */
    public RefreshReport refreshAll() {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SourceResult>> futures = new ArrayList<>();
            for (IndicatorSpec spec : specs) futures.add(pool.submit(() -> refreshOne(spec)));
            List<SourceResult> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    results.add(futures.get(i).get());
                } catch (InterruptedException | ExecutionException e) {
                    results.add(new SourceResult(specs.get(i).id(), false, String.valueOf(e.getMessage())));
                }
            }
            return new RefreshReport(Instant.now(), results);
        }
    }

    private SourceResult refreshOne(IndicatorSpec spec) {
        SourceConnector c = connectors.stream().filter(x -> x.supports(spec)).findFirst().orElse(null);
        if (c == null) return new SourceResult(spec.id(), false, "sin conector");
        SourceException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Series s = c.fetch(spec);
                if (s.points().isEmpty()) return new SourceResult(spec.id(), false, "la fuente no devolvió datos");
                store.put(s);
                return new SourceResult(spec.id(), true, null);
            } catch (SourceException e) {
                last = e;
            }
        }
        log.warn("Fuente falló: {}", spec.id(), last);
        return new SourceResult(spec.id(), false, last.getMessage());
    }
}
