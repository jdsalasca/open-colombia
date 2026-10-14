package top.eridu.opencolombia.refresh;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.source.IndicatorSpec;
import top.eridu.opencolombia.source.SourceCatalog;

@Component
public class RefreshScheduler {
    private final RefreshService service;
    private final SeriesStore store;

    public RefreshScheduler(RefreshService service, SeriesStore store) {
        this.service = service;
        this.store = store;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void daily() {
        service.refreshAll();
    }

    /** Primer arranque con caché vacía: poblar sin esperar al botón. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (store.all().isEmpty()) Thread.startVirtualThread(service::refreshAll);
    }

    @Configuration
    static class CatalogConfig {
        @Bean
        java.util.List<IndicatorSpec> indicatorSpecs() {
            return SourceCatalog.specs();
        }
    }
}
