package top.eridu.opencolombia.insight;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import top.eridu.opencolombia.analytics.Forecast;
import top.eridu.opencolombia.analytics.OlsResult;
import top.eridu.opencolombia.analytics.Stats;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.model.Insight;
import top.eridu.opencolombia.model.Insight.Confidence;
import top.eridu.opencolombia.model.Series;

@Service
public class InsightService {
    record Def(String theme, String kind, String x, String y, String title, String literature, String proposal) {}

    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final String NO_EVIDENCE =
            "No hay evidencia suficiente con los datos actuales para recomendar con base en esta relación.";

    private final SeriesStore store;
    private final List<Def> defs;

    public InsightService(SeriesStore store) {
        this.store = store;
        try (InputStream in = new ClassPathResource("insights.json").getInputStream()) {
            this.defs = List.of(JsonMapper.builder().build().readValue(in, Def[].class));
        } catch (IOException e) {
            throw new IllegalStateException("insights.json ilegible", e);
        }
    }

    public List<Insight> insights() {
        List<Insight> out = new ArrayList<>();
        for (Def d : defs) {
            Optional<Series> x = store.get(d.x());
            if (x.isEmpty()) continue;
            if (d.kind().equals("trend")) {
                out.add(trend(d, x.get()));
            } else {
                Optional<Series> y = store.get(d.y());
                if (y.isEmpty()) continue;
                out.add(relation(d, x.get(), y.get()));
            }
        }
        return out;
    }

    private static String level(int n, Double r2) {
        if (n < Stats.MIN_N) return "insuficiente";
        if (n >= 15 && r2 != null && r2 >= 0.6) return "alta";
        if (n >= 8) return "media";
        return "baja";
    }

    private static String f(double v) {
        return String.format(ES, "%.2f", v);
    }

    private Insight trend(Def d, Series s) {
        int n = s.points().size();
        Optional<OlsResult> t = Stats.trend(s);
        if (t.isEmpty()) {
            return new Insight(d.theme(), d.title(),
                    "Hay solo %d observaciones de «%s»; no se estima tendencia.".formatted(n, s.name()),
                    d.literature(), NO_EVIDENCE, new Confidence(n, null, "insuficiente"));
        }
        OlsResult r = t.get();
        Optional<Forecast> fc = Stats.forecast(s, 1);
        String last = s.latest().map(p -> "último dato %s (%d)".formatted(f(p.value()), p.date().getYear()))
                .orElse("sin último dato");
        String finding = "Tendencia de «%s»: %s %s por año (n = %d, R² = %s); %s.%s".formatted(
                s.name(), (r.slope() >= 0 ? "+" : "") + f(r.slope()), s.unit(), r.n(), f(r.r2()), last,
                fc.map(x -> " Con esa tendencia lineal, la proyección a un año es %s (intervalo 95%%: %s a %s)."
                        .formatted(f(x.value()), f(x.low()), f(x.high()))).orElse(""));
        String lvl = level(r.n(), r.r2());
        String proposal = lvl.equals("insuficiente") ? NO_EVIDENCE : d.proposal();
        return new Insight(d.theme(), d.title(), finding, d.literature(), proposal,
                new Confidence(r.n(), r.r2(), lvl));
    }

    private Insight relation(Def d, Series x, Series y) {
        Optional<OlsResult> o = Stats.regress(x, y);
        Optional<Double> corr = Stats.correlation(x, y);
        if (o.isEmpty()) {
            int n = Math.min(x.points().size(), y.points().size());
            return new Insight(d.theme(), d.title(),
                    "No hay suficientes años en común entre «%s» y «%s» para estimar la relación."
                            .formatted(x.name(), y.name()),
                    d.literature(), NO_EVIDENCE, new Confidence(n, null, "insuficiente"));
        }
        OlsResult r = o.get();
        String finding = ("Entre «%s» y «%s»: cada +1 %s en la primera se asocia con %s %s en la segunda "
                + "(n = %d, R² = %s, correlación = %s). Es una asociación estadística; no implica causalidad.")
                .formatted(x.name(), y.name(), x.unit(), (r.slope() >= 0 ? "+" : "") + f(r.slope()), y.unit(),
                        r.n(), f(r.r2()), corr.map(InsightService::f).orElse("n/d"));
        String lvl = level(r.n(), r.r2());
        return new Insight(d.theme(), d.title(), finding, d.literature(),
                lvl.equals("insuficiente") ? NO_EVIDENCE : d.proposal(), new Confidence(r.n(), r.r2(), lvl));
    }
}
