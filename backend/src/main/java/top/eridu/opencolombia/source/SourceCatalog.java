package top.eridu.opencolombia.source;

import static top.eridu.opencolombia.source.IndicatorSpec.Kind.SODA;
import static top.eridu.opencolombia.source.IndicatorSpec.Kind.WORLD_BANK;

import java.util.List;
import java.util.Map;
import top.eridu.opencolombia.model.Theme;

/** Indicadores verificados contra las APIs reales (ver docs/sources.md). */
public final class SourceCatalog {
    private SourceCatalog() {}

    private static IndicatorSpec wb(String id, Theme t, String name, String unit, String code) {
        return new IndicatorSpec(id, t, name, unit, "anual", WORLD_BANK, code, Map.of(), null, null);
    }

    public static List<IndicatorSpec> specs() {
        return List.of(
            wb("pib-crecimiento", Theme.ECONOMIA, "Crecimiento del PIB", "%", "NY.GDP.MKTP.KD.ZG"),
            wb("inflacion", Theme.ECONOMIA, "Inflación (IPC, variación anual)", "%", "FP.CPI.TOTL.ZG"),
            new IndicatorSpec("trm", Theme.ECONOMIA, "Tasa de cambio (TRM, promedio mensual)", "COP/USD",
                "mensual", SODA, "32sa-8pi3", Map.of(
                    "$select", "date_trunc_ym(vigenciadesde) as mes, avg(valor) as v",
                    "$where", "vigenciadesde >= '2010-01-01'",
                    "$group", "mes", "$order", "mes ASC", "$limit", "5000"), "v", "mes"),
            wb("desempleo", Theme.EMPLEO, "Desempleo", "% fuerza laboral", "SL.UEM.TOTL.ZS"),
            wb("empleo-vulnerable", Theme.EMPLEO, "Empleo vulnerable (proxy de informalidad)", "% del empleo",
                "SL.EMP.VULN.ZS"),
            wb("pobreza", Theme.POBREZA, "Pobreza (línea nacional)", "% población", "SI.POV.NAHC"),
            wb("gini", Theme.POBREZA, "Desigualdad (Gini)", "índice", "SI.POV.GINI"),
            new IndicatorSpec("homicidios-mensual", Theme.SEGURIDAD, "Homicidios (Policía, por mes)", "casos/mes",
                "mensual", SODA, "m8fd-ahd9", Map.of(
                    "$select", "date_trunc_ym(fecha_hecho) as mes, sum(cantidad) as v",
                    "$where", "fecha_hecho >= '2016-01-01'",
                    "$group", "mes", "$order", "mes ASC", "$limit", "5000",
                    "dropCurrentPeriod", "true"), "v", "mes"),
            wb("homicidios-tasa", Theme.SEGURIDAD, "Tasa de homicidios", "por 100 mil hab.", "VC.IHR.PSRC.P5"),
            wb("mortalidad-infantil", Theme.SALUD_EDUCACION, "Mortalidad infantil", "por 1.000 nacidos",
                "SP.DYN.IMRT.IN"),
            wb("gasto-salud", Theme.SALUD_EDUCACION, "Gasto en salud", "% del PIB", "SH.XPD.CHEX.GD.ZS"),
            wb("matricula-primaria", Theme.SALUD_EDUCACION, "Matrícula primaria neta", "%", "SE.PRM.NENR"),
            wb("bosque", Theme.AMBIENTE_ENERGIA, "Área de bosque", "% del territorio", "AG.LND.FRST.ZS"),
            wb("electricidad-pc", Theme.AMBIENTE_ENERGIA, "Consumo eléctrico per cápita", "kWh", "EG.USE.ELEC.KH.PC"));
    }
}
