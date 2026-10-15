package top.eridu.opencolombia.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.source.IndicatorSpec;

/** Cómo se obtiene y transforma cada indicador, con la URL exacta que se consulta a la fuente. */
@RestController
public class MethodologyController {
    public record Methodology(String id, String name, String theme, String description, String method,
                              String source, String sourceUrl, String queryUrl, String queryBody, String seriesUrl,
                              String periodicity, String unit, Boolean higherIsBetter) {}

    private final List<IndicatorSpec> specs;

    public MethodologyController(List<IndicatorSpec> specs) {
        this.specs = specs;
    }

    private static Methodology of(IndicatorSpec s) {
        return new Methodology(s.id(), s.name(), s.theme().id(), s.description(), s.method(), s.sourceName(),
                s.sourceUrl(), s.queryUrl(), s.queryBody(java.time.LocalDate.now()), "/api/series/" + s.id(),
                s.periodicity(), s.unit(), s.higherIsBetter());
    }

    @GetMapping("/api/methodology")
    public List<Methodology> all() {
        return specs.stream().map(MethodologyController::of).toList();
    }

    @GetMapping("/api/methodology/{id}")
    public ResponseEntity<Methodology> one(@PathVariable String id) {
        return specs.stream().filter(s -> s.id().equals(id)).findFirst()
                .map(s -> ResponseEntity.ok(of(s))).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
