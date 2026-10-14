package top.eridu.opencolombia.analytics;

/** Proyección puntual con intervalo de predicción al 95%. */
public record Forecast(double value, double low, double high) {}
