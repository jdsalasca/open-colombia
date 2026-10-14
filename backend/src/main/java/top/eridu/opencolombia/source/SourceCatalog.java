package top.eridu.opencolombia.source;

import static top.eridu.opencolombia.source.IndicatorSpec.Kind.SODA;
import static top.eridu.opencolombia.source.IndicatorSpec.Kind.WORLD_BANK;

import java.util.List;
import java.util.Map;
import top.eridu.opencolombia.model.Theme;

/** Indicadores verificados contra las APIs reales (ver docs/sources.md). */
public final class SourceCatalog {
    private SourceCatalog() {}

    private static IndicatorSpec wb(String id, Theme t, String name, String unit, String code,
                                    String description, String method, Boolean higherIsBetter) {
        return new IndicatorSpec(id, t, name, unit, "anual", WORLD_BANK, code, Map.of(), null, null,
                description, method, higherIsBetter);
    }

    private static final String WB_METHOD =
            "Se toma tal cual el valor anual publicado por el Banco Mundial para Colombia; los años sin dato se omiten.";

    public static List<IndicatorSpec> specs() {
        return List.of(
            wb("pib-crecimiento", Theme.ECONOMIA, "Crecimiento del PIB", "%", "NY.GDP.MKTP.KD.ZG",
                "Variación porcentual anual del producto interno bruto a precios constantes.", WB_METHOD, true),
            wb("inflacion", Theme.ECONOMIA, "Inflación (IPC, variación anual)", "%", "FP.CPI.TOTL.ZG",
                "Variación porcentual anual del índice de precios al consumidor.", WB_METHOD, false),
            new IndicatorSpec("trm", Theme.ECONOMIA, "Tasa de cambio (TRM, promedio mensual)", "COP/USD",
                "mensual", SODA, "32sa-8pi3", Map.of(
                    "$select", "date_trunc_ym(vigenciadesde) as mes, avg(valor) as v",
                    "$where", "vigenciadesde >= '2010-01-01'",
                    "$group", "mes", "$order", "mes ASC", "$limit", "5000"), "v", "mes",
                "Pesos colombianos por dólar: Tasa Representativa del Mercado certificada por la Superfinanciera.",
                "Promedio aritmético mensual de la TRM diaria (avg(valor) agrupado por mes, desde 2010). El mes en curso es parcial.",
                null),
            wb("desempleo", Theme.EMPLEO, "Desempleo", "% fuerza laboral", "SL.UEM.TOTL.ZS",
                "Porcentaje de la fuerza laboral sin empleo que busca trabajo (estimación modelada OIT).", WB_METHOD,
                false),
            wb("empleo-vulnerable", Theme.EMPLEO, "Empleo vulnerable (proxy de informalidad)", "% del empleo",
                "SL.EMP.VULN.ZS",
                "Trabajadores por cuenta propia y familiares sin remuneración, como porcentaje del empleo total.",
                WB_METHOD, false),
            wb("pobreza", Theme.POBREZA, "Pobreza (línea nacional)", "% población", "SI.POV.NAHC",
                "Porcentaje de la población bajo la línea de pobreza nacional.", WB_METHOD, false),
            wb("gini", Theme.POBREZA, "Desigualdad (Gini)", "índice", "SI.POV.GINI",
                "Índice de Gini del ingreso: 0 es igualdad perfecta y 100 desigualdad máxima.", WB_METHOD, false),
            new IndicatorSpec("homicidios-mensual", Theme.SEGURIDAD, "Homicidios (Policía, por mes)", "casos/mes",
                "mensual", SODA, "m8fd-ahd9", Map.of(
                    "$select", "date_trunc_ym(fecha_hecho) as mes, sum(cantidad) as v",
                    "$where", "fecha_hecho >= '2016-01-01'",
                    "$group", "mes", "$order", "mes ASC", "$limit", "5000",
                    "dropCurrentPeriod", "true"), "v", "mes",
                "Homicidios registrados por la Policía Nacional, contados por mes del hecho.",
                "Suma de la columna cantidad agrupada por mes de fecha_hecho (desde 2016). Se descarta el mes en curso por ser parcial.",
                false),
            wb("homicidios-tasa", Theme.SEGURIDAD, "Tasa de homicidios", "por 100 mil hab.", "VC.IHR.PSRC.P5",
                "Homicidios intencionales por cada 100.000 habitantes.", WB_METHOD, false),
            wb("mortalidad-infantil", Theme.SALUD_EDUCACION, "Mortalidad infantil", "por 1.000 nacidos",
                "SP.DYN.IMRT.IN", "Muertes de menores de un año por cada 1.000 nacidos vivos.", WB_METHOD, false),
            wb("gasto-salud", Theme.SALUD_EDUCACION, "Gasto en salud", "% del PIB", "SH.XPD.CHEX.GD.ZS",
                "Gasto corriente en salud (público y privado) como porcentaje del PIB.", WB_METHOD, null),
            wb("matricula-primaria", Theme.SALUD_EDUCACION, "Matrícula primaria neta", "%", "SE.PRM.NENR",
                "Niños en edad de primaria matriculados en primaria, como porcentaje de su grupo de edad.",
                WB_METHOD, true),
            wb("bosque", Theme.AMBIENTE_ENERGIA, "Área de bosque", "% del territorio", "AG.LND.FRST.ZS",
                "Superficie cubierta por bosque natural o plantado, como porcentaje del área terrestre.",
                WB_METHOD, true),
            wb("electricidad-pc", Theme.AMBIENTE_ENERGIA, "Consumo eléctrico per cápita", "kWh",
                "EG.USE.ELEC.KH.PC", "Consumo de energía eléctrica por habitante, en kWh al año.", WB_METHOD,
                null));
    }
}
