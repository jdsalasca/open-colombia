package top.eridu.opencolombia.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** «Hoy» es el día calendario de Colombia (UTC-5), no el de UTC: a las 22:00 en Bogotá ya es el día siguiente en UTC. */
class ClockZoneTest {
    @Test
    void appClockUsesBogotaSoTheCurrentDayIsNotTreatedAsCompleteTooEarly() {
        Clock c = new HttpConfig().clock();
        assertThat(c.getZone()).isEqualTo(ZoneId.of("America/Bogota"));
        Clock at2200Bogota = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), c.getZone());
        assertThat(LocalDate.now(at2200Bogota)).isEqualTo(LocalDate.of(2026, 10, 14));
    }
}
