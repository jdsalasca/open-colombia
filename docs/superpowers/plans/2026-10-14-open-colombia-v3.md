# Open Colombia v3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gráficos accesibles y compactos (A), once fuentes nuevas con refresco programático y frescura visible (B), y gráficos nuevos con un panel «Hoy en Colombia» (C).

**Architecture:** Se amplía el catálogo (`IndicatorSpec`) con política de refresco y vencimiento, se añaden conectores XM/Banrep/USGS y agregados diarios IDEAM, un `LiveService` con su endpoint `/api/live`, y un scheduler por frecuencia. El front tokeniza la paleta con un test de contraste, añade patrones SVG y leyendas tipo etiqueta, y componentes nuevos (StackedBars, HeatStrip, Histogram, SmallMultiples, TodayPanel).

**Tech Stack:** Igual que v2 (Java 25, Spring Boot 4.1.1, Commons Math 3; React 19, Vite, TypeScript, Vitest). Sin dependencias nuevas.

**Spec:** `docs/superpowers/specs/2026-10-14-open-colombia-v3-design.md`

## Global Constraints

- Contraste WCAG AA verificado por test: texto ≥ 4,5:1; gráficos y marcas ≥ 3:1, siempre sobre hoja `papel #f7f0e1`. Amarillo `#fcd116` solo como fondo con texto `#2b2118`.
- Paleta categórica (en orden): `#0b4f8a #a84300 #006b4f #9c3b78 #7a5c00 #00667a #59514a`; semánticos bueno `#1b6b3a`, malo `#b3261e`, neutro `#0b4f8a`; texto `#2b2118`, secundario `#6b5a48`.
- El significado nunca depende solo del color: patrones SVG distintos por categoría; marcas ▲/▼ con texto.
- Gráficas: alto máx 170 px (líneas), tortas ≤ 150 px; cuadrícula 2 columnas ≥ 900 px, 1 en móvil sin scroll horizontal a 375 px.
- Periodicidades: `anual`, `mensual`, `diaria`. Rangos: mensual `3m 6m 1a 2a todo`; anual `5a 10a todo`; diaria `7d 30d 90d 1a todo`. Ventanas inclusivas (v2).
- «En vivo» solo para TRM intradía y sismos; el resto se rotula con la fecha real de su último dato (XM llega con ~3 días de rezago).
- Refresco: `LIVE` cada 15 min, `DAILY` a las 06:00 y al arrancar si algún indicador está vencido; el botón refresca todo (1 por minuto). Un refresco en curso se comparte; un fallo total libera el límite; los errores al cliente no filtran texto de excepciones.
- IDEAM siempre se agrega en servidor (ventana de 120 días); XM en ventanas ≤ 31 días por consulta.
- Endpoints sin contrato oficial (Banrep intradía, XM) se rotulan así en la metodología y degradan a «sin dato».
- Sin librerías nuevas; repo público; sin secretos; despliegue por etapas (A, B, C) con verificación de los tres hostnames.

## Review Focus

- XM o Banrep caen/cambian formato: el resto del refresco y de la página sigue funcionando; el indicador muestra «sin dato» o su último valor con advertencia de frescura. (B3, B4, B5)
- Día de XM con < 20 horas válidas o con valores nulos: se descarta el día, no se promedia con huecos. (B3)
- Dos refrescos simultáneos (scheduler + botón): una sola ejecución upstream; el límite no queda bloqueado tras un fallo total. (B5)
- Tira de calor / histograma / dispersión con 0 datos o con huecos de días: mensaje claro, sin NaN. (C2, C3)
- Contraste: ningún token de `paper.css` ni color de gráfico baja del mínimo. (A1)
- Panel «Hoy» con cada fuente caída por separado: las demás tarjetas siguen. (C1)

## File Structure

```
backend/src/main/java/top/eridu/opencolombia/
  source/{IndicatorSpec,SourceCatalog,XmConnector,SodaConnector}.java   + refresh, staleAfterDays, sinceDaysAgo
  analytics/Range.java                                                   + D7, D30, D90 (días)
  model/{Series,Freshness}.java                                          + freshness
  live/{LiveSnapshot,LiveService,LiveStore,BanrepIntradayClient,UsgsClient,LiveController}.java
  refresh/{RefreshService,RefreshScheduler}.java                         política por frecuencia
web/src/
  paper/{palette.ts,ChartDefs.tsx,LegendTags.tsx,StackedBars.tsx,HeatStrip.tsx,Histogram.tsx,SmallMultiples.tsx}
  pages/{Today.tsx,Panorama}.tsx, paper.css (tokens)
```

---

## Parte A — Accesibilidad y diseño de gráficos

### Task A1: Tokens de color y test de contraste

**Files:** Create `web/src/paper/palette.ts`, `web/src/paper/palette.test.ts`; Modify `web/src/paper/paper.css`.

**Interfaces:**
- Produces: `export const PALETTE = {paper, ink, inkSoft, kraft, board, categorical: string[7], good, bad, neutral}`; `export function contrast(a: string, b: string): number` (WCAG 2.x, hex → ratio).

- [ ] **Step 1: Write failing test** (`palette.test.ts`): `contrast('#2b2118', '#f7f0e1')` ≈ 13.9 (±0.1); cada color de `categorical`, `good`, `bad`, `neutral` ≥ 4.5 sobre `paper`; `inkSoft` ≥ 4.5 sobre `paper`; `contrast('#fcd116', '#f7f0e1')` < 3 (documenta por qué es solo fondo) y `contrast('#2b2118', '#fcd116')` ≥ 4.5; test que parsea `paper.css`, extrae todos los `--*: #hex` de `:root` y falla si alguno de `--cat-*`, `--good`, `--bad`, `--neutral` no coincide con `PALETTE` o baja de 3:1 sobre `--paper`.
- [ ] **Step 2:** `cd web && npx vitest run src/paper/palette.test.ts` → FAIL.
- [ ] **Step 3:** Implementar `palette.ts`; en `paper.css` declarar `--cat-1..7`, `--good`, `--bad`, `--neutral`, reemplazar `--blue/--red/--green` por tokens nuevos y `.sheet-*` con texto ≥ 4,5:1; los paneles de gráficos (`.chart-sheet`) pasan a hoja `cream` (se elimina `color="kraft"` en `Breakdowns`).
- [ ] **Step 4:** test → PASS; `npm run build`. **Step 5:** commit `feat(a11y): tokenized palette with contrast test`.

### Task A2: Patrones, leyendas tipo etiqueta y tortas/barras accesibles

**Files:** Create `paper/ChartDefs.tsx`, `paper/LegendTags.tsx`; Modify `paper/PieChart.tsx`, `paper/HBarChart.tsx`, `paper/YoYBars.tsx`, `paper.css`; Test `paper/legend.test.tsx`.

**Interfaces:**
- Produces: `<ChartDefs />` (define `<pattern id="pat-0..6">`: sólido, rayas 45°, puntos, cuadrícula, rayas -45°, cruz, onda); `patternFill(i: number): string` → `url(#pat-i)`; `<LegendTags items={{label: string; value?: string; index: number}[]} />` (`<ul class="legend-tags">`, cada `li` con muestra SVG de 14×14 que repite color + patrón).

- [ ] **Step 1: Write failing tests:** `PieChart` con 3 ítems usa 3 `fill` distintos de `patternFill` (no solo color) y `LegendTags` muestra la misma muestra por ítem; `HBarChart` barras con patrón por categoría; `YoYBars` rellena barras buenas/malas con patrones distintos (`bar-good` rayas, `bar-bad` puntos); leyenda con 0 ítems no renderiza `<ul>`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar; reemplazar la leyenda actual de `PieChart` por `LegendTags`; tortas ≤ 150 px. **Step 4:** PASS + build. **Step 5:** commit.

### Task A3: Líneas compactas, marcas con forma, carga y menores de v2

**Files:** Modify `paper/LineChart.tsx`, `pages/ThemePage.tsx`, `components/RangeSelector.tsx`, `pages/MethodologyIndex.tsx`, `paper.css`; Test `paper/linechart.test.tsx`, `pages/pages.test.tsx`.

**Interfaces:**
- `LineChart` suma props `height?: number` (por defecto 170), `xLabel?: (date: string) => string`, `endLabel?: string`; marcas: mínimo `▼` y máximo `▲` como `<path>` con `<text>` «mín»/«máx» (color `neutral`, no rojo/verde); `trend` se recorta con `<clipPath>` al área del gráfico (no se fija cada extremo).
- `RangeSelector` suma `label: string` (aria-label del grupo); `Breakdowns` pasa «Dimensión» y «Periodo».

- [ ] **Step 1: Write failing tests:** marcas mín/máx tienen texto «mín» y «máx»; con tendencia `slope=2` sobre `0,0,0,0,10` la recta usa `clip-path` (no `y` fijado al borde); en series mensuales de ≤ 12 puntos las etiquetas del eje muestran mes («oct 2026»); `SeriesBlock` al cambiar de rango muestra «Cargando…» y no muestra tasas del rango previo; el grupo de dimensiones tiene `aria-label="Dimensión"`; glosario incluye «intervalo de predicción», «correlación de variaciones» y la nota «la anualizada y el cambio relativo no se muestran para series que ya son porcentajes».
- [ ] **Step 2:** FAIL. **Step 3:** Implementar (`setRes(null)` al inicio del efecto). **Step 4:** PASS + build + `tsc -b`. **Step 5:** commit.

### Task A4: Cuadrícula compacta, verificación visual y despliegue de A

- [ ] **Step 1:** `ThemePage` en `<div className="chart-grid">` (CSS: 2 columnas ≥ 900 px); `npm run build`, copiar a `backend/src/main/resources/static`, `mvn -q -B -f backend/pom.xml package`, arrancar jar local.
- [ ] **Step 2:** En el navegador integrado: contraste visual de pie y barras, patrones visibles, leyendas, 375 px sin desborde, sin errores de consola. Corregir lo que falle.
- [ ] **Step 3:** `git push origin main`; en el servidor `git pull --ff-only` + `sudo docker compose -f compose.tunnel.yaml up -d --build`; verificar los tres hostnames, bundle servido = `web/dist`.

---

## Parte B — Fuentes nuevas y refresco programático

### Task B1: Periodicidad diaria, rangos por días y frescura

**Files:** Modify `analytics/Range.java`, `source/IndicatorSpec.java`, `model/Series.java`, `api/SeriesController.java`, `api/IndicatorController.java`; Create `model/Freshness.java`; Test `analytics/RatesTest.java`, `api/SeriesApiTest.java`, `model/FreshnessTest.java`.

**Interfaces:**
- Produces:
  - `Range` suma `D7("7d", days 7)`, `D30("30d", 30)`, `D90("90d", 90)` con `int days()`; `months()==0` para ellos; `availableFor("diaria")` = `D7, D30, D90, Y1, ALL`; `apply` por días resta `days` en lugar de meses.
  - `record Freshness(LocalDate lastDataDate, long ageDays, int staleAfterDays, boolean stale)`; `Series` suma componente `Freshness freshness` (null en caché antigua) y constructor secundario de 9 argumentos para compatibilidad; `Series withFreshness(Freshness)`.
  - `IndicatorSpec` suma `RefreshClass refresh` (`LIVE|DAILY|SLOW`) y `int staleAfterDays`; constructor secundario de 13 argumentos (defaults `SLOW`, 400). `static Freshness Freshness.of(Series s, int staleAfterDays, LocalDate today)`.
  - `/api/indicators`, `/api/themes/{id}` y `/api/series/{id}` devuelven cada `Series` con `freshness` calculado con la fecha de hoy; vencidos: anual 800 días, mensual 75, diaria 5.

- [ ] **Step 1: Write failing tests:** `Range.D30.apply` sobre 100 días deja 31 puntos; `availableFor("diaria")`; `Freshness.of` con último dato hace 3 días y `staleAfterDays=5` → `stale=false`, `ageDays=3`; con 6 días → `stale=true`; serie vacía → `lastDataDate=null`, `stale=true`; `/api/series/{id}` incluye `series.freshness.stale`; `range=7d` sobre serie mensual → 400.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** `mvn -q -B -f backend/pom.xml test` → PASS. **Step 5:** commit.

### Task B2: Indicadores de Policía e IDEAM (agregados en servidor)

**Files:** Modify `source/IndicatorSpec.java` (`queryUrl(LocalDate today)`), `source/SodaConnector.java`, `source/SourceCatalog.java`, `docs/sources.md`; Test `source/SodaConnectorTest.java`, `source/SourceCatalogTest.java`, fixtures `soda-daily.json`.

**Interfaces:**
- `IndicatorSpec.queryUrl(LocalDate today)`; `queryUrl()` = `queryUrl(LocalDate.now())`. Parámetros internos nuevos (no viajan a la fuente): `sinceDaysAgo` (días) y `sinceField` (columna): el `queryUrl` añade `$where=<sinceField> >= '<today − sinceDaysAgo>'` combinado con cualquier `$where` existente con `AND`.
- Catálogo (todos en español, con `description`, `method`, `higherIsBetter=false` en delitos): `hurto-personas` (`4rxi-8m8d`), `extorsion` (`q2ib-t9am`), `secuestro` (`d7zw-hpf4`), `delitos-informaticos` (`4v6r-wu98`), `violencia-intrafamiliar` (`gepp-dxcs`) → tema SEGURIDAD, mensual, `$select=date_trunc_ym(fecha_hecho) as mes, sum(cantidad) as v`, `dropCurrentPeriod=true`, `refresh=DAILY`, `staleAfterDays=75`; `temperatura-nacional` (`sbwg-7ju4`, `avg(valorobservado)`, filtro de sanidad `valorobservado between -5 and 45`) y `precipitacion-nacional` (`s54a-sgyg`) → AMBIENTE_ENERGIA, diaria, `sinceDaysAgo=120`, `date_trunc_ymd(fechaobservacion) as dia`, `staleAfterDays=5`, `refresh=DAILY`.

- [ ] **Step 1: Verify first** (curl real): para `s54a-sgyg` probar `sum(valorobservado)/count(distinct codigoestacion)` por día; si la API no lo admite, usar `avg(valorobservado)` y rotular en `method`: «promedio por lectura de todas las estaciones, no lluvia acumulada». Grabar fixtures reales (≈ 5 filas) de cada tipo en `src/test/resources/fixtures/`.
- [ ] **Step 2: Write failing tests:** `queryUrl(LocalDate.of(2026,10,14))` de `temperatura-nacional` contiene `fechaobservacion >= '2026-06-16'` y no contiene `sinceDaysAgo`; el conector parsea `dia` ISO y descarta filas sin valor; el catálogo cubre los 5 delitos con `higherIsBetter=false`; ids únicos; `SourceCatalogTest` sigue cubriendo los 6 temas.
- [ ] **Step 3:** FAIL. **Step 4:** Implementar y actualizar `docs/sources.md` con las fuentes, columnas, y fecha de verificación. **Step 5:** `mvn test` PASS; `curl` real de cada `queryUrl` responde 200. **Step 6:** commit.

### Task B3: Conector XM (precio de bolsa y demanda)

**Files:** Create `source/XmConnector.java`; Modify `source/IndicatorSpec.java` (`Kind.XM`, campo de cuerpo), `source/SourceCatalog.java`, `api/MethodologyController.java`; Test `source/XmConnectorTest.java`, fixtures `xm-PrecBolsNaci.json`, `xm-DemaReal.json`, `xm-sparse.json`.

**Interfaces:**
- `IndicatorSpec.Kind.XM`; `ref` = MetricId; `params`: `agg` = `avg|sum`, `scale` = divisor numérico, `entity` = `Sistema`, `windowDays` = `120`.
- `XmConnector implements SourceConnector`: por ventanas de ≤ 31 días llama `POST https://servapibi.xm.com.co/hourly` con `{"MetricId","StartDate","EndDate","Entity","Filter":[]}`; por cada `Items[].Date` agrega `Hour01..Hour24` (`avg` o `sum`, ÷ `scale`); descarta el día con < 20 horas numéricas válidas.
- Catálogo: `energia-precio-bolsa` (`PrecBolsNaci`, avg, COP/kWh) y `energia-demanda` (`DemaReal`, sum, scale 1_000_000 → GWh) → AMBIENTE_ENERGIA, diaria, `staleAfterDays=7`, `refresh=DAILY`.
- `Methodology` suma `queryBody: String|null` (JSON del POST); la página muestra el endpoint, el cuerpo en `<code>` y la nota «endpoint público sin contrato oficial».

- [ ] **Step 1: Write failing tests:** (fixture real) 2 días → 2 puntos con promedio correcto de 24 horas; demanda → suma ÷ 1e6; `xm-sparse.json` (día con 10 horas) → día descartado; HTTP 400/500 → `SourceException`; `Items: []` → serie vacía (el refresco la trata como «no devolvió datos» y conserva la anterior); 120 días → 4 ventanas de ≤ 31 días (MockRestServiceServer con 4 expectativas); `/api/methodology/energia-precio-bolsa` incluye `queryBody` con `"MetricId":"PrecBolsNaci"`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS. **Step 5:** `curl` real de una ventana corta confirma 200. **Step 6:** commit.

### Task B4: Datos en vivo — TRM intradía y sismos

**Files:** Create `live/LiveSnapshot.java`, `live/BanrepIntradayClient.java`, `live/UsgsClient.java`, `live/LiveStore.java`, `live/LiveService.java`, `live/LiveController.java`; Test `live/LiveServiceTest.java`, `live/LiveApiTest.java`, fixtures `banrep-intraday.json`, `usgs-quakes.json`.

**Interfaces:**
- Produces:
  - `record Tick(Instant t, double v)`; `record UsdCop(List<Tick> ticks, double last, double open, double changePct, Instant lastTickAt)` (ticks reducidas a ≤ 120 por muestreo uniforme conservando primero y último).
  - `record Quake(Instant time, double mag, String place, double lat, double lon, double depthKm)`.
  - `record LiveSnapshot(UsdCop usdCop, List<Quake> quakes, Instant fetchedAt, Map<String,String> errors)`; `errors` indica qué fuente falló.
  - `LiveService.refresh()`: consulta ambas fuentes de forma independiente (una caída no borra la otra ni su último valor); persiste en `live.json`; `LiveService.get()` desde caché; `GET /api/live` → `LiveSnapshot` (200 con campos null si nunca cargó).
  - USGS: `format=geojson`, bbox `minlatitude=-4.5&maxlatitude=13.5&minlongitude=-79.5&maxlongitude=-66`, `minmagnitude=2.5`, `starttime = hoy − 30 d`, `orderby=time`, `limit=200`.
  - Banrep: `GET .../consultaMercadoCambiario` → `[[ "epoch_ms", "valor" ], …]`.

- [ ] **Step 1: Write failing tests:** (fixtures reales) 1100 ticks → ≤ 120 conservando primero y último; `open` = primer tick, `changePct` correcto; 0 ticks → `usdCop=null` sin excepción; USGS parsea `mag/place/time/coords`; USGS 500 con Banrep ok → `usdCop` presente, `quakes` conserva el valor previo y `errors` contiene `usgs`; ambos caen con caché previa → se sirve la caché; `/api/live` con caché vacía → 200.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar con `RestClient` existente. **Step 4:** PASS. **Step 5:** commit.

### Task B5: Refresco por frecuencia y robustez

**Files:** Modify `refresh/RefreshService.java`, `refresh/RefreshScheduler.java`, `api/RefreshController.java`, `api/RateLimiter.java`; Test `refresh/RefreshServiceTest.java`, `api/ApiTest.java`.

**Interfaces:**
- `RefreshService.refreshAll()` pasa a compartir la ejecución en curso: una segunda llamada concurrente devuelve el mismo `RefreshReport` sin consultar de nuevo; `RefreshReport refreshClass(RefreshClass c)` refresca solo esa clase; `RefreshService.anyStale(LocalDate today): boolean` (algún indicador con `Freshness.stale`).
- `RefreshScheduler`: `@Scheduled(fixedRate = 15 min)` → `LiveService.refresh()`; `@Scheduled(cron = "0 0 6 * * *")` → `refreshClass(DAILY)`; arranque: refresca si la caché está vacía **o** `anyStale`.
- `RateLimiter.release()`: `RefreshController` lo invoca si **todos** los resultados fallaron; el cuerpo de error al cliente es genérico («Una o más fuentes no respondieron») y el detalle solo va al log.

- [ ] **Step 1: Write failing tests:** dos hilos llaman `refreshAll()` a la vez con un conector lento → el conector se invoca una vez por indicador; refresco con todos fallando → `RateLimiter` permite un nuevo intento de inmediato; `SourceResult.error` no contiene nombres de clase ni rutas; `refreshClass(DAILY)` no toca indicadores `SLOW`; arranque con serie vencida dispara refresco.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar (`ReentrantLock` + `CompletableFuture` compartido). **Step 4:** `mvn test` PASS. **Step 5:** commit.

### Task B6: Verificación e integración de B y despliegue

- [ ] **Step 1:** Jar local con datos reales: `POST /api/refresh` → todas las series nuevas con puntos; `GET /api/live` con ticks y sismos reales; `GET /api/series/temperatura-nacional?range=30d` con tasas; las `queryUrl` de metodología responden 200.
- [ ] **Step 2:** Desplegar en el servidor y verificar los tres hostnames, `/api/live`, y `POST /api/refresh` 25/25 (14 previas + 11 nuevas, donde TRM intradía y sismos van por `LiveService`).

---

## Parte C — Gráficos nuevos y panel «Hoy en Colombia»

### Task C1: Panel «Hoy» y tipos/cliente del front

**Files:** Create `web/src/pages/Today.tsx`; Modify `types.ts`, `api.ts`, `App.tsx`, `pages/Home.tsx`, `paper/LineChart.tsx`; Test `pages/today.test.tsx`.

**Interfaces:**
- `getLive(): Promise<LiveSnapshot>`; tipos espejo; `<Today live indicators />` con 4 tarjetas: **Dólar hoy** (línea intradía con `xLabel` de hora y variación vs. apertura, rótulo «en vivo» con `lastTickAt`), **Último sismo** (M, lugar, hace cuánto; conteo M ≥ 2,5 en 7 días), **Temperatura nacional** (último dato y comparación con el promedio de 30 d, con su fecha), **Precio de bolsa** (último día disponible con su fecha real). Cada tarjeta lleva frescura y enlace a metodología; si su fuente falló muestra «sin dato» y las demás siguen.

- [ ] **Step 1: Write failing tests:** con `live.usdCop=null` la tarjeta de dólar muestra «sin dato» y las demás se renderizan; el rótulo «en vivo» solo aparece en dólar y sismos; la fecha de precio de bolsa se muestra tal cual (`2026-10-11`), nunca «ayer»; `LineChart` con `xLabel` usa el formateador.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar; `Home` renderiza `<Today />` al inicio. **Step 4:** PASS + build. **Step 5:** commit.

### Task C2: Barras apiladas «Panorama de delitos» y pequeños múltiplos

**Files:** Create `paper/StackedBars.tsx`, `paper/SmallMultiples.tsx`, `pages/Panorama.tsx`; Modify `pages/ThemePage.tsx`; Test `paper/stacked.test.tsx`.

**Interfaces:**
- `<StackedBars categories={string[]} periods={string[]} values={number[][]} />` (values[p][c]; patrones por categoría con `patternFill`, leyenda `LegendTags`, tabla de datos); `<SmallMultiples series={{label: string; points: Point[]}[]} />` (mini-líneas con misma escala temporal y etiqueta final directa).
- `Panorama` toma de `data.themes` las 5 series de delitos de SEGURIDAD y arma los últimos 12 meses comunes; faltantes → celda 0 marcada en la tabla como «sin dato».

- [ ] **Step 1: Write failing tests:** 3 categorías × 4 periodos → 12 `rect` con 3 patrones distintos; una categoría con todos ceros no rompe la escala; serie faltante aparece como «sin dato» en la tabla; `SmallMultiples` con 0 puntos muestra «sin datos» sin NaN.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS + build. **Step 5:** commit.

### Task C3: Tira de calor diaria, histograma y dispersión de sismos

**Files:** Create `paper/HeatStrip.tsx`, `paper/Histogram.tsx`, `paper/QuakeScatter.tsx`; Modify `pages/ThemePage.tsx` (ambiente-energía); Test `paper/heat.test.tsx`.

**Interfaces:**
- `<HeatStrip points={Point[]} unit={string} days={number} />`: una celda por día calendario (huecos = celda vacía con patrón de tramas), escala secuencial de un solo matiz con contraste ≥ 3:1 entre extremos y texto en la tabla de datos.
- `<Histogram values={number[]} binWidth={number} unit />` (bins de magnitud 0,5); `<QuakeScatter quakes />` (magnitud vs. tiempo; radio ∝ magnitud, etiqueta del mayor evento).

- [ ] **Step 1: Write failing tests:** 90 días con 3 huecos → 90 celdas, 3 con clase `gap`; todos los valores iguales → una sola intensidad sin NaN; histograma de `[]` → «sin sismos en el periodo»; bins de `[2.5, 2.6, 3.1]` con ancho 0,5 → conteos `[2,0,1]`; dispersión con un evento no divide por cero.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS + build. **Step 5:** commit.

### Task C4: Integración final, verificación visual y despliegue de C

- [ ] **Step 1:** Build local completo (web + jar), datos reales: recorrer portada «Hoy», `/tema/seguridad` (panorama + múltiplos), `/tema/ambiente-energia` (tira de calor, histograma, dispersión), `/metodologia/energia-precio-bolsa` (muestra `queryBody`), 375 px sin desborde, sin errores de consola, tabla de datos en cada gráfica.
- [ ] **Step 2:** `git push`, despliegue en el servidor, verificación pública: tres hostnames, `/api/live`, `/api/series/precipitacion-nacional?range=30d`, bundle servido = `web/dist`, `POST /api/refresh` 25/25.
