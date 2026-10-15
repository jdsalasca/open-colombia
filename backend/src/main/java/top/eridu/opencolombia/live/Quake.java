package top.eridu.opencolombia.live;

import java.time.Instant;

public record Quake(Instant time, double mag, String place, double lat, double lon, double depthKm) {}
