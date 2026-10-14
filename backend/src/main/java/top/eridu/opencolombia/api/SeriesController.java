package top.eridu.opencolombia.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.analytics.Range;
import top.eridu.opencolombia.analytics.Rates;
import top.eridu.opencolombia.analytics.RatesResult;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.source.IndicatorSpec;

/** Una serie vista por ventana de tiempo, con sus tasas. Opera sobre datos ya guardados. */
@RestController
public class SeriesController {
    public record SeriesResponse(Series series, String range, List<String> availableRanges, RatesResult rates,
                                 Boolean higherIsBetter) {}

    private final SeriesStore store;
    private final List<IndicatorSpec> specs;

    public SeriesController(SeriesStore store, List<IndicatorSpec> specs) {
        this.store = store;
        this.specs = specs;
    }

    /**
     * Tasas de la ventana. Una serie cuya unidad ya es un porcentaje (inflación, desempleo) no tiene tasa
     * «anualizada» con sentido: anualizar la variación de una tasa es engañoso, así que se omite.
     */
    private static RatesResult rates(Series windowed, IndicatorSpec spec) {
        return Rates.compute(windowed).map(r -> spec.unit().startsWith("%")
                ? new RatesResult(r.n(), r.from(), r.to(), r.change(), r.changePct(), null, r.mean(), r.min(),
                        r.max(), r.stdDev())
                : r).orElse(null);
    }

    @GetMapping("/api/series/{id}")
    public ResponseEntity<?> series(@PathVariable String id,
                                    @RequestParam(name = "range", defaultValue = "todo") String rangeCode) {
        Optional<IndicatorSpec> spec = specs.stream().filter(s -> s.id().equals(id)).findFirst();
        if (spec.isEmpty()) return ResponseEntity.notFound().build();

        List<Range> available = Range.availableFor(spec.get().periodicity());
        List<String> codes = available.stream().map(Range::code).toList();
        Optional<Range> range = Range.parse(rangeCode).filter(available::contains);
        if (range.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Rango no válido para una serie " + spec.get().periodicity() + ": " + rangeCode,
                    "availableRanges", codes));
        }
        Series full = store.get(id).orElseGet(() -> spec.get().placeholder());
        Series windowed = range.get().apply(full);
        return ResponseEntity.ok(new SeriesResponse(windowed, range.get().code(), codes,
                rates(windowed, spec.get()), spec.get().higherIsBetter()));
    }
}
