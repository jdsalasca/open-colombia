package top.eridu.opencolombia.breakdown;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.eridu.opencolombia.analytics.Range;
import top.eridu.opencolombia.source.SourceException;

@RestController
public class BreakdownController {
    private static final List<Range> RANGES = List.of(Range.M3, Range.M6, Range.Y1, Range.Y2);

    private final BreakdownService service;

    public BreakdownController(BreakdownService service) {
        this.service = service;
    }

    @GetMapping("/api/breakdowns/homicidios")
    public ResponseEntity<?> homicidios(@RequestParam String dim,
                                        @RequestParam(defaultValue = "1a") String range) {
        Optional<Range> r = Range.parse(range).filter(RANGES::contains);
        if (!BreakdownService.isDimension(dim) || r.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "dim o range no válidos",
                    "dims", List.of("sexo", "zona", "arma", "departamento"),
                    "availableRanges", RANGES.stream().map(Range::code).toList()));
        }
        try {
            return ResponseEntity.ok(service.get(dim, r.get()));
        } catch (SourceException e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "No pudimos consultar datos.gov.co en este momento. Intenta de nuevo más tarde."));
        }
    }
}
