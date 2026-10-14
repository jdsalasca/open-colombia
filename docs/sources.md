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
