# Fuentes verificadas (2026-10-13)

Todas consultadas contra la API real el 2026-10-13. Sin llave.

## Banco Mundial (`api.worldbank.org/v2/country/COL/indicator/<code>`), anual
| Código | Indicador | Último dato |
|---|---|---|
| NY.GDP.MKTP.KD.ZG | Crecimiento del PIB (%) | 2025: 2.64 |
| FP.CPI.TOTL.ZG | Inflación IPC (% anual) | 2025: 5.14 |
| SL.UEM.TOTL.ZS | Desempleo (% fuerza laboral, modelado OIT) | 2025: 8.29 |
| SL.EMP.VULN.ZS | Empleo vulnerable (% empleo; proxy de informalidad) | 2025: 42.96 |
| SI.POV.NAHC | Pobreza línea nacional (% población) | 2024: 31.8 |
| SI.POV.GINI | Índice de Gini | 2024: 54.4 |
| VC.IHR.PSRC.P5 | Homicidios por 100 mil hab. | 2023: 24.9 |
| SP.DYN.IMRT.IN | Mortalidad infantil (por 1.000 nacidos) | 2024: 10.5 |
| SH.XPD.CHEX.GD.ZS | Gasto corriente en salud (% PIB) | 2024: 8.14 |
| SE.PRM.NENR | Matrícula primaria neta (%) | 2018: 92.9 |
| AG.LND.FRST.ZS | Área de bosque (% territorio) | 2024: 53.7 |
| EG.USE.ELEC.KH.PC | Consumo eléctrico per cápita (kWh) | 2025: 1636 |

## datos.gov.co (SODA, `www.datos.gov.co/resource/<id>.json`)
| Dataset | Uso | Nota |
|---|---|---|
| `32sa-8pi3` TRM (Superfinanciera) | Promedio mensual de TRM | Diaria, actualizada a diario |
| `m8fd-ahd9` HOMICIDIO (Policía Nacional) | Homicidios por mes (`sum(cantidad)`) | Hasta 2026-08; el último mes puede ser parcial |

## Descartadas
- DANE desempleo/IPC mensual: no hay dataset oficial en datos.gov.co (búsqueda sin resultados útiles); se usa Banco Mundial anual.
- `axk9-g2nh` tasas de captación: por entidad financiera, no una serie nacional.
- Cobertura educativa y deforestación en datos.gov.co: solo datasets municipales/departamentales dispersos.

## Añadidas en v3 (verificadas 2026-10-14)
| Dataset | Uso | Nota |
|---|---|---|
| `4rxi-8m8d` Hurto a personas (Policía) | suma mensual de `cantidad` | hasta 2026-08 |
| `q2ib-t9am` Extorsión (Policía) | suma mensual | hasta 2026-08 |
| `d7zw-hpf4` Secuestro (Policía) | suma mensual | hasta 2026-08 |
| `4v6r-wu98` Delitos informáticos (Policía) | suma mensual | hasta 2026-08 |
| `gepp-dxcs` Violencia intrafamiliar (Policía) | suma mensual | hasta 2026-08 |
| `sbwg-7ju4` Temperatura del aire (IDEAM) | `avg(valorobservado)` por día, -5 a 45 °C | ~20 mil lecturas/día; llega hasta ayer |
| `s54a-sgyg` Precipitación (IDEAM) | `sum(valorobservado) / count(distinct codigoestacion)` por día | ~82 mil lecturas/día, ~476 estaciones; tarda ~8 s |

Los datasets de IDEAM se agregan siempre en servidor con una ventana móvil de 120 días.

Nota de calidad (2026-10-14): en las series de la Policía el último mes publicado llega incompleto (agosto 2026 aparecía
17-48 % por debajo de julio, sin que ocurriera en 2025). Se descarta con `dropLastPeriods=1`. En la temperatura de IDEAM la
propia fuente tiene ~18 días sin datos (25 jul - 11 ago 2026): se muestran como huecos.
