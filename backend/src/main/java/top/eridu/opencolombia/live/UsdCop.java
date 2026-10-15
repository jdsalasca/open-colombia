package top.eridu.opencolombia.live;

import java.time.Instant;
import java.util.List;

/** Cotización intradía del dólar. {@code open} es el primer dato del día y {@code changePct} es último vs. apertura. */
public record UsdCop(List<Tick> ticks, double last, double open, double changePct, Instant lastTickAt) {}
