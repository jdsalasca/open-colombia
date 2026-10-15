package top.eridu.opencolombia.refresh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.cache.SeriesStore;
import top.eridu.opencolombia.live.LiveService;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;
import top.eridu.opencolombia.source.IndicatorSpec.RefreshClass;

class RefreshSchedulerTest {
    @TempDir Path dir;

    private RefreshService refresh;
    private LiveService live;
    private SeriesStore store;
    private RefreshScheduler scheduler;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        refresh = mock(RefreshService.class);
        live = mock(LiveService.class);
        store = new SeriesStore(dir);
        scheduler = new RefreshScheduler(refresh, store, live, clock, true);
    }

    @Test
    void liveTickRefreshesOnlyTheLiveSources() {
        scheduler.live();
        verify(live).refresh();
        verify(refresh, never()).refreshAll();
        verify(refresh, never()).refreshClass(any());
    }

    @Test
    void dailyRefreshesDailyIndicatorsAndLive() {
        scheduler.daily();
        verify(refresh).refreshClass(RefreshClass.DAILY);
        verify(live).refresh();
    }

    @Test
    void startupWithEmptyCacheRefreshesEverything() {
        when(refresh.anyStale(any(LocalDate.class))).thenReturn(true);
        scheduler.warmUpNow();
        verify(refresh).refreshAll();
        verify(live).refresh();
    }

    @Test
    void startupWithFreshCacheOnlyRefreshesLive() {
        store.put(new Series("a", "economia", "A", "%", "t", "u", "diaria",
                List.of(new Point(LocalDate.of(2026, 10, 13), 1)), Instant.EPOCH));
        when(refresh.anyStale(any(LocalDate.class))).thenReturn(false);
        scheduler.warmUpNow();
        verify(refresh, never()).refreshAll();
        verify(live).refresh();
    }

    @Test
    void startupWithAStaleSeriesRefreshesEverything() {
        store.put(new Series("a", "economia", "A", "%", "t", "u", "diaria",
                List.of(new Point(LocalDate.of(2026, 9, 1), 1)), Instant.EPOCH));
        when(refresh.anyStale(any(LocalDate.class))).thenReturn(true);
        scheduler.warmUpNow();
        verify(refresh).refreshAll();
    }

    @Test
    void disabledSchedulerDoesNothing() {
        var off = new RefreshScheduler(refresh, store, live, clock, false);
        off.live();
        off.daily();
        off.warmUpNow();
        verify(live, never()).refresh();
        verify(refresh, never()).refreshAll();
    }
}
