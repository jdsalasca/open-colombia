# Open Colombia v3 — diseño

Fecha: 2026-10-14 · Estado: pendiente de revisión · Base: v2 desplegada (`2026-10-14-open-colombia-v2-design.md`)

## 1. Objetivo
Más datos y más frescos, obtenidos de forma programática; gráficos más compactos, legibles y
accesibles. Éxito: (1) hay un panel «Hoy en Colombia» con datos de las últimas horas; (2) cada indicador
declara su frescura; (3) la paleta y los gráficos cumplen WCAG AA y no dependen solo del color;
(4) las gráficas ocupan menos espacio y traen leyendas estilo etiqueta.

## 2. Decisiones ya tomadas
- Tres sub-proyectos en orden: **A** accesibilidad y diseño de gráficos → **B** fuentes y refresco →
  **C** gráficos nuevos y panel «Hoy». Cada uno se despliega por separado.
- Mismo stack; sin librerías nuevas de gráficos ni de caché.
- Se incluye el endpoint **no documentado** de TRM intradía del Banco de la República, con degradación
  elegante («sin dato») y etiquetado como tal en la metodología.
- Fuera de v3 (v4): SECOP II, tráfico portuario, exportaciones agrícolas, regalías; DANE mensual (sin API) y
  FMI (bloqueado).

## 3. A — Accesibilidad y diseño de gráficos
**Paleta verificada (contraste WCAG calculado):**
| Uso | Color | sobre papel `#f7f0e1` |
|---|---|---|
| texto | `#2b2118` | 13,9:1 |
| texto secundario | `#6b5a48` | 5,8:1 (sobre kraft: no se usa como texto) |
| categórica 1–7 | `#0b4f8a` `#a84300` `#006b4f` `#9c3b78` `#7a5c00` `#00667a` `#59514a` | 5,3–7,4:1 |
| bueno / malo / neutro | `#1b6b3a` / `#b3261e` / `#0b4f8a` | 5,8 / 5,8 / 7,4 |
El amarillo bandera `#fcd116` queda **solo como fondo** con texto `#2b2118` (10,7:1); en v2 fallaba como
color de gráfico (1,30:1).
- Los gráficos van siempre sobre hoja `papel`, nunca sobre kraft (en v2 rojo/verde/terracota rendían 2,0–2,9:1).
- **No solo color:** tortas y barras apiladas con patrones SVG distintos (rayas, puntos, cuadrícula, cruz);
  marcas de mín/máx con forma (▲/▼) y etiqueta de texto; etiquetas directas al final de las líneas.
- Un test (Vitest) lee los tokens de `paper.css` y falla si algún par texto/fondo o gráfico/fondo baja de
  4,5:1 (texto) o 3:1 (gráficos).
- Foco visible 3 px, `aria-pressed`, `aria-label` propio por grupo de chips (se arregla «Rango de tiempo»
  anunciado para dimensiones), tablas de datos ya existentes.

**Tamaño y leyendas:**
- Líneas: alto máx 170 px (antes ~220), cuadrícula de 2 columnas ≥ 900 px y 1 en móvil; tortas ≤ 150 px.
- Leyenda tipo **etiqueta de papel**: `<ul class="legend-tags">` con muestra (color + patrón), nombre y valor,
  recortes de esquina, legible en móvil.
- Minors de v2 resueltos aquí: marcas mín/máx con significado (5), estado «cargando» al cambiar de rango
  sin mostrar datos del rango previo (5), recorte correcto de la recta de tendencia con `clipPath` (8),
  glosario completo (9), etiqueta de eje con mes en series mensuales (11).

## 4. B — Fuentes nuevas y refresco programático
Todas verificadas el 2026-10-14 contra la API real, sin llave:
| Id | Fuente | Dato | Periodicidad | Último dato |
|---|---|---|---|---|
| `hurto-personas` | datos.gov.co `4rxi-8m8d` | hurto a personas, suma mensual | mensual | 2026-08 |
| `extorsion` | `q2ib-t9am` | extorsión | mensual | 2026-08 |
| `secuestro` | `d7zw-hpf4` | secuestro | mensual | 2026-08 |
| `delitos-informaticos` | `4v6r-wu98` | delitos informáticos | mensual | 2026-08 |
| `violencia-intrafamiliar` | `gepp-dxcs` | violencia intrafamiliar | mensual | 2026-08 |
| `temperatura-nacional` | IDEAM `sbwg-7ju4` | promedio diario de todas las estaciones | diaria | ayer |
| `precipitacion-nacional` | IDEAM `s54a-sgyg` | promedio diario | diaria | ayer |
| `energia-precio-bolsa` | XM `servapibi.xm.com.co/hourly` `PrecBolsNaci` | promedio diario COP/kWh | diaria | ayer |
| `energia-demanda` | XM `DemaReal` | suma diaria kWh | diaria | ayer |
| (en vivo) TRM intradía | `totoro.banrep.gov.co/estadisticas-economicas/rest/consultaDatosService/consultaMercadoCambiario` | ticks `[epoch_ms, valor]` | minutos | hoy |
| (en vivo) sismos | USGS `earthquake.usgs.gov/fdsnws/event/1/query` bbox Colombia, M ≥ 2,5 | evento | minutos | hoy |

- `Point` sigue siendo fecha; se añade periodicidad `diaria` y `Range` gana rangos por días:
  diaria → `7d`, `30d`, `90d`, `1a`, `todo`. El resto no cambia.
- **Datos en vivo** (`GET /api/live`): `{usdCop:{ticks,last,open,changePct,fetchedAt}, quakes:[{time,mag,place,
  lat,lon,depth}], fetchedAt}`; se guarda en `live.json` y se sirve desde caché.
- **Política de refresco por indicador** (`IndicatorSpec.refresh`): `LIVE` cada 15 min, `DAILY` diario a las
  06:00 y al arrancar si está vencido, `SLOW` diario. El botón sigue refrescando todo (1 por minuto).
- **Frescura:** cada serie expone `lastDataDate` y `staleAfterDays`; la UI muestra «dato de hace 2 h»,
  «ayer» o «ago 2026», y una advertencia si superó su vencimiento esperado.
- **Robustez de refresco** (minors de v1 que ahora importan por el scheduler): un refresco en curso se
  comparte entre quienes lo piden (sin duplicar consultas); un fallo total libera el límite del botón;
  los mensajes de error al cliente no filtran texto de excepciones.
- **Conectores:** `BanrepIntradayConnector`, `UsgsConnector`, `XmConnector` (ventanas ≤ 31 días por
  consulta, según `MaxDays` de su listado), `SodaDailyAggregate` (IDEAM agrega en servidor con
  `date_trunc_ymd` + `avg`). Cada uno con fixture grabada de la API real y prueba de contrato.

## 5. C — Gráficos nuevos y «Hoy en Colombia»
- **Panel «Hoy»** (portada): dólar hoy (línea intradía + variación vs. apertura), último sismo y conteo de
  sismos M ≥ 2,5 en 7 días, temperatura media nacional de ayer vs. promedio 30 d, precio de bolsa de ayer.
  Cada tarjeta lleva su frescura y su enlace a metodología.
- **Barras apiladas** «Panorama de delitos»: los 5 delitos nuevos por mes (últimos 12 m), con patrones.
- **Tira de calor diaria** (temperatura y lluvia, 90 días): escala secuencial de un solo matiz con contraste
  verificado + valores en la tabla de datos.
- **Histograma** de magnitudes de sismos (30 días) y **dispersión** magnitud vs. tiempo.
- **Pequeños múltiplos** de delitos: una mini-línea por delito, misma escala temporal, etiqueta directa.
- Todos con tabla de datos, patrones y paleta de la sección 3.

## 6. Pruebas
- Contraste (Vitest): tokens de `paper.css` cumplen 4,5:1 / 3:1; ningún gráfico usa un color no tokenizado.
- Conectores: fixtures reales (feliz, vacío, valores nulos, HTTP 500, timeout), agregación diaria correcta,
  ventana ≤ 31 días en XM, TRM intradía con 0 ticks.
- `Range` diaria; frescura (`lastDataDate`, vencida/no vencida); refresco compartido y liberación de límite.
- Front: patrones presentes en cada categoría, estado de carga al cambiar rango, histograma con 0 sismos,
  tira de calor con huecos, panel «Hoy» con cada fuente caída (sin romper el resto).

## 7. Riesgos
- Endpoints sin contrato oficial (Banrep, XM): cambio de formato → fixtures + «sin dato» + advertencia.
- IDEAM precipitación tarda ~8 s por agregación diaria: se agrega por ventana de 90 días y se cachea.
- Volumen de datos IDEAM (≈ 80 k lecturas/día): siempre agregado en servidor, nunca descargado crudo.
- Frescura honesta: datos de ayer no se rotulan «en vivo»; solo TRM y sismos llevan esa etiqueta.
