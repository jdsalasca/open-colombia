package top.eridu.opencolombia.source;

import static top.eridu.opencolombia.source.IndicatorSpec.Kind.SODA;
import static top.eridu.opencolombia.source.IndicatorSpec.Kind.WORLD_BANK;
import static top.eridu.opencolombia.source.IndicatorSpec.Kind.XM;

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

    /** Serie mensual de la Policía Nacional: suma de {@code cantidad} por mes del hecho, desde 2016. */
    private static IndicatorSpec police(String id, String name, String dataset, String description) {
        // el dato más reciente (sin el último mes, que llega incompleto) tiene ~110 días: vence a los 130
        return new IndicatorSpec(id, Theme.SEGURIDAD, name, "casos/mes", "mensual", SODA, dataset, Map.of(
                "$select", "date_trunc_ym(fecha_hecho) as mes, sum(cantidad) as v",
                "$where", "fecha_hecho >= '2016-01-01'",
                "$group", "mes", "$order", "mes ASC", "$limit", "5000",
                "dropCurrentPeriod", "true", "dropLastPeriods", "1"), "v", "mes",
                description,
                "Suma de la columna cantidad agrupada por mes de fecha_hecho (desde 2016). Se descarta el mes en curso y "
                        + "el último mes publicado, porque la Policía lo consolida con rezago (en agosto 2026 aparecía con 17 a 48 % "
                        + "menos casos que julio, lo que parecería una caída real). Son denuncias registradas: no incluyen delitos "
                        + "no denunciados.",
                false, IndicatorSpec.RefreshClass.DAILY, 130);
    }

    private static final String XM_METHOD =
            "API pública de XM (servapibi.xm.com.co/hourly), sin contrato oficial; consultas de hasta 31 días. "
                    + "El último dato disponible llega con unos 3 días de rezago.";

    private static final String IDEAM_BASE = "Promedio simple de las lecturas de todas las estaciones automáticas del IDEAM "
            + "(no está ponderado por área ni altitud); ventana móvil de 120 días, consultada en 4 bloques de 30 días. "
            + "El día en curso se descarta por incompleto.";

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
            police("hurto-personas", "Hurto a personas (Policía, por mes)", "4rxi-8m8d",
                "Denuncias de hurto a personas registradas por la Policía Nacional."),
            police("extorsion", "Extorsión (Policía, por mes)", "q2ib-t9am",
                "Denuncias de extorsión registradas por la Policía Nacional."),
            police("secuestro", "Secuestro (Policía, por mes)", "d7zw-hpf4",
                "Casos de secuestro registrados por la Policía Nacional."),
            police("delitos-informaticos", "Delitos informáticos (Policía, por mes)", "4v6r-wu98",
                "Denuncias de delitos informáticos (hurto por medios informáticos, etc.) registradas por la Policía."),
            police("violencia-intrafamiliar", "Violencia intrafamiliar (Policía, por mes)", "gepp-dxcs",
                "Casos de violencia intrafamiliar registrados por la Policía Nacional."),
            wb("homicidios-tasa", Theme.SEGURIDAD, "Tasa de homicidios", "por 100 mil hab.", "VC.IHR.PSRC.P5",
                "Homicidios intencionales por cada 100.000 habitantes.", WB_METHOD, false),
            wb("mortalidad-infantil", Theme.SALUD_EDUCACION, "Mortalidad infantil", "por 1.000 nacidos",
                "SP.DYN.IMRT.IN", "Muertes de menores de un año por cada 1.000 nacidos vivos.", WB_METHOD, false),
            wb("gasto-salud", Theme.SALUD_EDUCACION, "Gasto en salud", "% del PIB", "SH.XPD.CHEX.GD.ZS",
                "Gasto corriente en salud (público y privado) como porcentaje del PIB.", WB_METHOD, null),
            wb("matricula-primaria", Theme.SALUD_EDUCACION, "Matrícula primaria neta", "%", "SE.PRM.NENR",
                "Niños en edad de primaria matriculados en primaria, como porcentaje de su grupo de edad.",
                WB_METHOD, true),
            new IndicatorSpec("temperatura-nacional", Theme.AMBIENTE_ENERGIA,
                "Temperatura del aire (promedio nacional diario)", "°C", "diaria", SODA, "sbwg-7ju4", Map.of(
                    "$select", "date_trunc_ymd(fechaobservacion) as dia, avg(valorobservado) as v",
                    "$where", "valorobservado between -5 and 45",
                    "$group", "dia", "$order", "dia ASC", "$limit", "400",
                    "sinceDaysAgo", "120", "sinceField", "fechaobservacion", "chunkDays", "30", "dropCurrentDay", "true"), "v", "dia",
                "Temperatura del aire a 2 m medida por las estaciones automáticas del IDEAM.",
                IDEAM_BASE + " Se excluyen lecturas fuera de -5 a 45 °C.",
                null),
            new IndicatorSpec("precipitacion-nacional", Theme.AMBIENTE_ENERGIA,
                "Lluvia (mm por estación por día)", "mm/día", "diaria", SODA, "s54a-sgyg", Map.of(
                    "$select", "date_trunc_ymd(fechaobservacion) as dia, sum(valorobservado) as s, count(distinct codigoestacion) as n",
                    "$where", "valorobservado >= 0 AND valorobservado < 500",
                    "$group", "dia", "$order", "dia ASC", "$limit", "400",
                    "sinceDaysAgo", "120", "sinceField", "fechaobservacion", "chunkDays", "30", "dropCurrentDay", "true",
                    "divideBy", "n"), "s", "dia",
                "Precipitación acumulada del día, en promedio por estación automática del IDEAM.",
                "Suma diaria de las lecturas de precipitación dividida por el número de estaciones que reportaron ese día. "
                        + "Se excluyen lecturas negativas o mayores de 500 mm. " + IDEAM_BASE,
                null),
            new IndicatorSpec("energia-precio-bolsa", Theme.AMBIENTE_ENERGIA,
                "Precio de bolsa de energía (promedio diario)", "COP/kWh", "diaria", XM, "PrecBolsNaci", Map.of(
                    "agg", "avg", "scale", "1", "entity", "Sistema", "windowDays", "120"), null, null,
                "Precio de la energía en la bolsa nacional (Precio Bolsa Nacional por Sistema), promedio de las 24 horas del día.",
                XM_METHOD + " Promedio de las 24 horas; se descarta el día con menos de 20 horas válidas.", null),
            new IndicatorSpec("energia-generacion", Theme.AMBIENTE_ENERGIA,
                "Generación eléctrica del sistema (total diario)", "GWh/día", "diaria", XM, "Gene", Map.of(
                    "agg", "sum", "scale", "1000000", "entity", "Sistema", "windowDays", "120"), null, null,
                "Generación neta de las plantas nacionales en sus puntos de frontera (métrica Gene, entidad Sistema).",
                XM_METHOD + " Suma de las 24 horas en kWh dividida por 1.000.000 (GWh); se descarta el día sin las 24 horas.", null),
            wb("bosque", Theme.AMBIENTE_ENERGIA, "Área de bosque", "% del territorio", "AG.LND.FRST.ZS",
                "Superficie cubierta por bosque natural o plantado, como porcentaje del área terrestre.",
                WB_METHOD, true),
            wb("electricidad-pc", Theme.AMBIENTE_ENERGIA, "Consumo eléctrico per cápita", "kWh",
                "EG.USE.ELEC.KH.PC", "Consumo de energía eléctrica por habitante, en kWh al año.", WB_METHOD,
                null));
    }
}
