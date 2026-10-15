package top.eridu.opencolombia.refresh;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.live.LiveService;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.IndicatorSpec.RefreshClass;
import top.eridu.opencolombia.source.SourceCatalog;

/** Refresco por frecuencia: en vivo cada 15 min, diario a las 06:00 y al arrancar si algo falta o venció. */
@Component
public class RefreshScheduler {
    private final RefreshService service;
    private final SeriesStore store;
    private final LiveService live;
    private final Clock clock;
    private final boolean enabled;

    public RefreshScheduler(RefreshService service, SeriesStore store, LiveService live, Clock clock,
                            @Value("${opencolombia.refresh.enabled:true}") boolean enabled) {
        this.service = service;
        this.store = store;
        this.live = live;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** TRM intradía y sismos. */
    @Scheduled(fixedRate = 15 * 60 * 1000L, initialDelay = 15 * 60 * 1000L)
    public void live() {
        if (!enabled) return;
        live.refresh();
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void daily() {
        if (!enabled) return;
        service.refreshClass(RefreshClass.DAILY);
        live.refresh();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (enabled) Thread.startVirtualThread(this::warmUpNow);
    }

    /** Arranque: lo en vivo siempre; el resto solo si la caché está vacía o algún indicador venció. */
    public void warmUpNow() {
        if (!enabled) return;
        if (store.all().isEmpty() || service.anyStale(LocalDate.now(clock))) service.refreshAll();
        live.refresh();
    }

    @Configuration
    static class CatalogConfig {
        @Bean
        java.util.List<IndicatorSpec> indicatorSpecs() {
            return SourceCatalog.specs();
        }
    }
}
