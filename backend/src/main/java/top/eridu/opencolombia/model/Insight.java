package top.eridu.opencolombia.model;

/** Hallazgo econométrico + literatura + propuesta, con su nivel de confianza explícito. */
public record Insight(String themeId, String title, String finding, String literature, String proposal,
                      Confidence confidence) {
    /** level ∈ alta | media | baja | insuficiente. r2 es null en tendencias sin ajuste. */
    public record Confidence(int n, Double r2, String level) {}
}
