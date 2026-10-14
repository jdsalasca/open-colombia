package top.eridu.opencolombia.model;

import java.util.Arrays;
import java.util.Optional;

public enum Theme {
    ECONOMIA("economia", "Economía"),
    EMPLEO("empleo", "Empleo"),
    POBREZA("pobreza", "Pobreza y desigualdad"),
    SEGURIDAD("seguridad", "Seguridad"),
    SALUD_EDUCACION("salud-educacion", "Salud y educación"),
    AMBIENTE_ENERGIA("ambiente-energia", "Ambiente y energía");

    private final String id;
    private final String label;

    Theme(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() { return id; }
    public String label() { return label; }

    public static Optional<Theme> byId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }
}
