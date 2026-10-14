package top.eridu.opencolombia.analytics;

import java.time.LocalDate;
import top.eridu.opencolombia.model.Point;

/** Tasas de una ventana. {@code changePct} y {@code annualizedPct} son null cuando no están definidas. */
public record RatesResult(int n, LocalDate from, LocalDate to, double change, Double changePct,
                          Double annualizedPct, double mean, Point min, Point max, double stdDev) {}
