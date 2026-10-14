package top.eridu.opencolombia.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.eridu.opencolombia.model.Point;
import top.eridu.opencolombia.model.Series;

class SeriesStoreTest {
    @TempDir Path dir;

    private Series s(String id) {
        return new Series(id, "economia", "N", "%", "src", "http://s", "anual",
                List.of(new Point(LocalDate.of(2024, 1, 1), 1.5)), Instant.parse("2026-10-13T00:00:00Z"));
    }

    @Test
    void putThenGetRoundTrips() {
        var store = new SeriesStore(dir);
        store.put(s("a"));
        assertThat(store.get("a")).contains(s("a"));
    }

    @Test
    void allOnMissingDirIsEmpty() {
        assertThat(new SeriesStore(dir.resolve("nope")).all()).isEmpty();
    }

    @Test
    void corruptFileIsIgnored() throws Exception {
        var store = new SeriesStore(dir);
        store.put(s("good"));
        Files.writeString(dir.resolve("bad.json"), "{not json");
        assertThat(store.get("bad")).isEmpty();
        assertThat(store.all()).extracting(Series::id).containsExactly("good");
    }
}
