package top.eridu.opencolombia.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.source.SourceException;

class LiveServiceTest {
    @TempDir Path dir;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

    private static UsdCop usd(double last) {
        var t = new Tick(Instant.parse("2026-10-14T11:59:00Z"), last);
        return new UsdCop(List.of(new Tick(Instant.parse("2026-10-14T08:00:00Z"), 3200), t), last, 3200,
                (last - 3200) / 3200 * 100, t.t());
    }

    private static Quake quake(double mag) {
        return new Quake(Instant.parse("2026-10-14T05:00:00Z"), mag, "5 km N of Cepitá", 6.9, -73.0, 150);
    }

    interface Fn<T> { T get() throws SourceException; }

    private static UsdCopSource usdSource(Fn<Optional<UsdCop>> f) {
        return () -> f.get();
    }

    private static QuakeSource quakeSource(Fn<List<Quake>> f) {
        return () -> f.get();
    }

    @Test
    void bothSourcesOkProduceAFreshSnapshotAndPersistIt() {
        var svc = new LiveService(usdSource(() -> Optional.of(usd(3181.7))), quakeSource(() -> List.of(quake(4.8))),
                new LiveStore(dir), CLOCK);
        var snap = svc.refresh();
        assertThat(snap.usdCop().last()).isEqualTo(3181.7);
        assertThat(snap.quakes()).hasSize(1);
        assertThat(snap.errors()).isEmpty();
        assertThat(snap.fetchedAt()).isEqualTo(Instant.parse("2026-10-14T12:00:00Z"));
        // otra instancia (otro proceso) lee lo persistido
        assertThat(new LiveService(usdSource(() -> Optional.empty()), quakeSource(List::of), new LiveStore(dir), CLOCK)
                .get().usdCop().last()).isEqualTo(3181.7);
    }

    @Test
    void oneSourceDownKeepsItsPreviousValueAndTheOtherStillUpdates() {
        var usdOk = new boolean[] {true};
        var svc = new LiveService(
                usdSource(() -> Optional.of(usd(3181.7))),
                quakeSource(() -> {
                    if (usdOk[0]) return List.of(quake(4.8));
                    throw new SourceException("USGS caído");
                }),
                new LiveStore(dir), CLOCK);
        svc.refresh();
        usdOk[0] = false;
        var snap = svc.refresh();
        assertThat(snap.usdCop()).isNotNull();
        assertThat(snap.quakes()).hasSize(1); // conserva el último valor
        assertThat(snap.errors()).containsKey("usgs").doesNotContainKey("banrep");
        assertThat(snap.errors().get("usgs")).doesNotContain("Exception").doesNotContain("SourceException");
    }

    @Test
    void bothDownServesTheCachedSnapshotAndReportsBothErrors() {
        var fail = new boolean[] {false};
        var svc = new LiveService(
                usdSource(() -> {
                    if (fail[0]) throw new SourceException("x");
                    return Optional.of(usd(3181.7));
                }),
                quakeSource(() -> {
                    if (fail[0]) throw new SourceException("y");
                    return List.of(quake(3.1));
                }),
                new LiveStore(dir), CLOCK);
        var first = svc.refresh();
        fail[0] = true;
        var snap = svc.refresh();
        assertThat(snap.usdCop()).isEqualTo(first.usdCop());
        assertThat(snap.quakes()).isEqualTo(first.quakes());
        assertThat(snap.errors()).containsKeys("banrep", "usgs");
        assertThat(snap.fetchedAt()).isEqualTo(first.fetchedAt()); // no se rotula como actualizado
    }

    @Test
    void neverLoadedIsAValidEmptySnapshot() {
        var svc = new LiveService(usdSource(() -> Optional.empty()), quakeSource(List::of), new LiveStore(dir), CLOCK);
        var snap = svc.get();
        assertThat(snap.usdCop()).isNull();
        assertThat(snap.quakes()).isEmpty();
        assertThat(snap.fetchedAt()).isNull();
    }

    @Test
    void zeroTicksTodayIsNotAnError() {
        var svc = new LiveService(usdSource(() -> Optional.empty()), quakeSource(() -> List.of(quake(2.6))),
                new LiveStore(dir), CLOCK);
        var snap = svc.refresh();
        assertThat(snap.usdCop()).isNull();
        assertThat(snap.errors()).doesNotContainKey("banrep");
        assertThat(snap.quakes()).hasSize(1);
    }

    @Test
    void corruptStoreFileIsTreatedAsEmpty() throws Exception {
        java.nio.file.Files.writeString(dir.resolve("live.json"), "{no es json");
        var svc = new LiveService(usdSource(() -> Optional.empty()), quakeSource(List::of), new LiveStore(dir), CLOCK);
        assertThat(svc.get().usdCop()).isNull();
    }
}
