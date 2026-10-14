package top.eridu.opencolombia.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeriesTest {
    private Series of(List<Point> pts) {
        return new Series("x", "economia", "X", "%", "src", "http://s", "anual", pts, Instant.EPOCH);
    }

    @Test
    void latestReturnsMaxDateEvenIfUnordered() {
        var s = of(List.of(new Point(LocalDate.of(2022, 1, 1), 2), new Point(LocalDate.of(2024, 1, 1), 4),
                new Point(LocalDate.of(2023, 1, 1), 3)));
        assertThat(s.latest()).contains(new Point(LocalDate.of(2024, 1, 1), 4));
    }

    @Test
    void latestOfEmptyIsEmpty() {
        assertThat(of(List.of()).latest()).isEmpty();
    }

    @Test
    void themeById() {
        assertThat(Theme.byId("economia")).contains(Theme.ECONOMIA);
        assertThat(Theme.byId("x")).isEmpty();
    }
}
