# Open Colombia v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que cada cifra muestre su dato anterior y su metodología, que las series se vean por rangos con tasas, y sumar gráficos de tendencia, barras y composición.

**Architecture:** Se amplía el backend Spring Boot existente con `Range`/`Rates` (analytics), metadatos de metodología en `IndicatorSpec`, y dos endpoints nuevos (`/api/series/{id}`, `/api/breakdowns/homicidios`). El front React añade selector de rangos, panel de tasas, página de metodología y gráficos SVG propios.

**Tech Stack:** Igual que v1 (Java 25, Spring Boot 4.1.1, Commons Math 3; React 19, Vite, TypeScript, Vitest). Sin dependencias nuevas.

**Spec:** `docs/superpowers/specs/2026-10-14-open-colombia-v2-design.md` (base v1: `2026-10-13-open-colombia-design.md`)

## Global Constraints

- Sin librerías nuevas de gráficos ni de caché; gráficas SVG propias con el estilo paper craft de `web/src/paper/paper.css`.
- Rangos válidos: series `mensual` → `3m`, `6m`, `1a`, `2a`, `todo`; series `anual` → `5a`, `10a`, `todo`. Rango inválido → HTTP 400 con `availableRanges`.
- `rates` es `null` con < 2 puntos; `annualizedPct` solo si el rango abarca ≥ 12 meses y el valor inicial > 0, con `((v_fin / v_ini)^(365.25/días) − 1) × 100`.
- Tendencia solo con ≥ 5 puntos (`Stats.MIN_N`); si no, se indica y no se dibuja.
- Tortas solo para homicidios (`sexo`, `zona`, `arma`); `departamento` en barras horizontales (top 8 + «otros»); ≤ 6 categorías + «otros» en tortas.
- Una única función construye la URL upstream (`IndicatorSpec.queryUrl()`); conector y metodología la usan.
- Cada gráfica trae tabla de datos accesible en `<details>` y respeta `prefers-reduced-motion`; mobile 375 px sin scroll horizontal.
- «Recargar por periodo» = cambiar ventana sobre datos guardados; no consulta fuentes.
- Repo público `jdsalasca/open-colombia`; sin secretos; despliegue como v1 (build en el servidor, `compose.tunnel.yaml`, puerto 5174), verificando los tres hostnames.

## Review Focus

- Rango inválido para la periodicidad (p. ej. `3m` en serie anual, `xx`): 400 con los rangos válidos, no 500 ni serie vacía. (Task 3)
- Serie con 0–1 puntos dentro del rango: `rates=null`, la UI muestra «insuficiente», sin NaN. (Tasks 1, 6)
- Valor inicial ≤ 0 o rango < 12 m: `annualizedPct=null`, no ∞/NaN. (Task 1)
- Breakdown con 0 o 1 categorías, o datos.gov.co caído: lista vacía → «sin datos»; caída → 502 con mensaje, sin colgar. (Tasks 4, 7)
- Serie con un solo punto: la tarjeta no muestra «vs. anterior» ni falla. (Task 5)
- Todos los puntos iguales (varianza 0): tendencia/volatilidad sin NaN. (Tasks 1, 7)

## File Structure

```
backend/src/main/java/top/eridu/opencolombia/
  analytics/{Range,Rates,RatesResult}.java        ventanas y tasas
  source/IndicatorSpec.java                       + description, method, higherIsBetter, queryUrl()
  source/SourceCatalog.java                       metadatos de los 14 indicadores
  api/{SeriesController,MethodologyController}.java
  breakdown/{BreakdownService,BreakdownController,Breakdown}.java
web/src/
  types.ts, api.ts, format.ts                     tipos y cliente ampliados
  components/{RangeSelector,RatesPanel,DataTable}.tsx
  paper/{PieChart,HBarChart,YoYBars}.tsx, LineChart.tsx (tendencia)
  pages/{Methodology,MethodologyIndex}.tsx, ThemePage.tsx, Breakdowns.tsx
```

---

### Task 1: Rangos y tasas (analytics)

**Files:**
- Create: `analytics/Range.java`, `analytics/Rates.java`, `analytics/RatesResult.java`
- Test: `backend/src/test/java/top/eridu/opencolombia/analytics/RatesTest.java`

**Interfaces:**
- Consumes: `Series`, `Point`.
- Produces:
  - `enum Range { M3("3m"), M6("6m"), Y1("1a"), Y2("2a"), Y5("5a"), Y10("10a"), ALL("todo") }` con `String code()`, `static Optional<Range> parse(String)`, `static List<Range> availableFor(String periodicity)`, `Series apply(Series s)` (puntos con fecha > último − duración; `ALL` sin recorte).
  - `record RatesResult(int n, LocalDate from, LocalDate to, double change, Double changePct, Double annualizedPct, double mean, Point min, Point max, double stdDev)`.
  - `static Optional<RatesResult> Rates.compute(Series s)` — `Optional.empty()` con < 2 puntos; `changePct` null si valor inicial = 0; `stdDev` muestral (0 si varianza 0).

- [ ] **Step 1: Write failing tests** (`RatesTest`): serie anual 100,110,121 → `change=21`, `changePct=21.0`, `mean=110.33…`, min 100, max 121; `Range.Y5.apply` sobre 30 años deja 5 puntos y conserva el último; `availableFor("mensual")` = M3,M6,Y1,Y2,ALL y `availableFor("anual")` = Y5,Y10,ALL; `parse("xx")` vacío; 1 punto → `Rates.compute` vacío (0 puntos también); `annualizedPct` null en un rango de 6 meses y con valor inicial 0; con 24 meses de 100→121 (mensual, 25 puntos) ≈ 10 % anual; todos iguales → `stdDev=0` sin NaN.
- [ ] **Step 2:** `mvn -q -B -f backend/pom.xml test -Dtest=RatesTest` → FAIL (clases inexistentes).
- [ ] **Step 3:** Implementar con las firmas de Interfaces; días = `ChronoUnit.DAYS.between(from,to)`, anualizada solo si días ≥ 360.
- [ ] **Step 4:** mismo comando → PASS. **Step 5:** commit `feat: ranges and rates`.

### Task 2: Metodología en el catálogo y `queryUrl` única

**Files:**
- Modify: `source/IndicatorSpec.java`, `source/SourceCatalog.java`, `source/WorldBankConnector.java`, `source/SodaConnector.java`
- Create: `api/MethodologyController.java`
- Test: `source/SourceCatalogTest.java`, `api/MethodologyApiTest.java`, existentes de conectores

**Interfaces:**
- Produces:
  - `IndicatorSpec` suma componentes `String description, String method, Boolean higherIsBetter` y un constructor secundario con la firma anterior de 10 parámetros (defaults: `description=name`, `method=""`, `higherIsBetter=null`) para no romper los tests existentes.
  - `String IndicatorSpec.queryUrl()` — WORLD_BANK: `https://api.worldbank.org/v2/country/COL/indicator/<ref>?format=json&per_page=200`; SODA: `https://www.datos.gov.co/resource/<ref>.json` + solo los params que empiezan con `$`, ya codificados.
  - `Series` no cambia; `GET /api/methodology/{id}` → `{id,name,description,method,source,sourceUrl,queryUrl,seriesUrl,periodicity,unit,higherIsBetter}` (404 si no existe); `GET /api/methodology` → lista de los mismos.

- [ ] **Step 1: Write failing tests:** catálogo — todo indicador tiene `description` y `method` no vacíos; `higherIsBetter` definido según la tabla (pib true, inflacion/desempleo/empleo-vulnerable/pobreza/gini/homicidios-mensual/homicidios-tasa/mortalidad-infantil false, matricula-primaria/bosque true, trm/gasto-salud/electricidad-pc null); `MethodologyApiTest`: `/api/methodology/trm` 200 con `queryUrl` que empieza por `https://www.datos.gov.co/resource/32sa-8pi3.json?` y contiene `%24select`o `$select`; `/api/methodology/nope` 404; los conectores existentes pasan sin cambios (la URL que piden coincide con `queryUrl()`).
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar; ambos conectores hacen `URI.create(spec.queryUrl())`; borrar la construcción duplicada de URL. Textos de `description`/`method` en español, uno por indicador (p. ej. TRM: «Promedio mensual de la TRM diaria publicada por la Superfinanciera»). **Step 4:** `mvn -q -B -f backend/pom.xml test` → PASS. **Step 5:** commit.

### Task 3: Endpoint de series con rango

**Files:**
- Create: `api/SeriesController.java`
- Test: `api/SeriesApiTest.java`

**Interfaces:**
- Consumes: `SeriesStore`, `List<IndicatorSpec>`, `Range`, `Rates`, `IndicatorSpec.placeholder()`.
- Produces: `GET /api/series/{id}?range=<code>` → `{series: Series, range: "6m", availableRanges: ["3m",…], rates: RatesResult|null}`. Sin `range` → `todo`. Serie desconocida → 404; rango inválido o no disponible para su periodicidad → 400 `{error, availableRanges}`; serie sin cargar → 200 con placeholder y `rates=null`.

- [ ] **Step 1: Write failing tests** (con series sembradas en el store de `@DynamicPropertySource`): TRM mensual sembrada 30 meses + `range=6m` → 6 puntos y `rates.n=6`; `range=5a` sobre serie mensual → 400 con `availableRanges` que contiene `3m`; `range=3m` sobre anual → 400; `range=xx` → 400; id desconocido → 404; serie no cargada → 200, `points=[]`, `rates=null`; sin `range` → `range="todo"`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar el controlador. **Step 4:** Run → PASS. **Step 5:** commit.

### Task 4: Composición de homicidios (breakdown)

**Files:**
- Create: `breakdown/Breakdown.java`, `breakdown/BreakdownService.java`, `breakdown/BreakdownController.java`
- Test: `breakdown/BreakdownServiceTest.java`, `breakdown/BreakdownApiTest.java`, fixture `src/test/resources/fixtures/breakdown-sexo.json` (grabada de la API real)

**Interfaces:**
- Consumes: `RestClient` (bean `sourcesRestClient`), `Clock`, `Range`.
- Produces:
  - `record Breakdown(String dim, String range, LocalDate from, double total, List<Item> items, String source, String queryUrl)` con `record Item(String label, double value, double share)`; `share` en 0–1.
  - `Breakdown BreakdownService.get(String dim, Range range) throws SourceException` — dims `sexo→sexo`, `zona→zona`, `arma→arma_medio`, `departamento→departamento`; consulta `m8fd-ahd9` con `$select=<col> as k, sum(cantidad) as v`, `$where=fecha_hecho >= '<from>'`, `$group=k`, `$order=v DESC`, `$limit=50`; `from` = hoy − duración del rango (solo `3m,6m,1a,2a`); tortas ≤ 6 + «OTROS», departamento top 8 + «OTROS»; caché en memoria 1 h por `dim+range` con el `Clock` inyectado.
  - `GET /api/breakdowns/homicidios?dim=…&range=…` → `Breakdown`; dim/range inválidos → 400; `SourceException` → 502 `{error}`.

- [ ] **Step 1: Write failing tests:** con la fixture de sexo: 3 items, `share` suma 1.0 y `total` = suma; 9 departamentos → 8 + «OTROS» agregando el resto; solo 1 categoría → 1 item con share 1.0; respuesta `[]` → `items=[]`, `total=0` sin NaN; segunda llamada dentro de 1 h no vuelve a pedir (MockRestServiceServer con `once()`); tras avanzar el reloj 61 min vuelve a pedir; `dim=nope` y `range=5a` → 400 (ApiTest); upstream 500 → 502.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar; reloj mutable en el test. **Step 4:** Run → PASS. **Step 5:** commit.

### Task 5: Front — tipos, cliente y «dato anterior»

**Files:**
- Modify: `web/src/types.ts`, `web/src/api.ts`, `web/src/format.ts`, `web/src/paper/PaperCard.tsx`
- Test: `web/src/format.test.ts`, `web/src/paper/paper.test.tsx`

**Interfaces:**
- Produces: tipos `Rates`, `SeriesResponse`, `Methodology`, `Breakdown`; `getSeries(id: string, range?: string): Promise<SeriesResponse>`, `getMethodology(id)`, `getMethodologyIndex()`, `getBreakdown(dim, range)`; en `format.ts`: `previous(s: Series): Point | null` (penúltimo punto por fecha).
- `PaperCard` muestra «▲ 1,15 vs. 2024: 3,99 <unidad>» y un enlace «¿cómo se calcula?» a `/metodologia/<id>`.

- [ ] **Step 1: Write failing tests:** `previous` devuelve el penúltimo por fecha aun desordenada, y `null` con 1 o 0 puntos; `PaperCard` con dos puntos muestra el texto `vs. 2024` y el valor anterior formateado; con 1 punto no muestra «vs.»; el enlace tiene `href` `/metodologia/<id>`.
- [ ] **Step 2:** `cd web && npx vitest run` → FAIL. **Step 3:** Implementar. **Step 4:** PASS + `npm run build` sin errores TS. **Step 5:** commit.

### Task 6: Front — selector de rangos, tasas y metodología

**Files:**
- Create: `components/RangeSelector.tsx`, `components/RatesPanel.tsx`, `pages/Methodology.tsx`, `pages/MethodologyIndex.tsx`
- Modify: `pages/ThemePage.tsx`, `App.tsx` (rutas `/metodologia`, `/metodologia/:id`)
- Test: `components/components.test.tsx`, `pages/methodology.test.tsx`

**Interfaces:**
- Consumes: `getSeries`, `getMethodology`.
- Produces: `<RangeSelector ranges={string[]} value onChange />` (botones con `aria-pressed`); `<RatesPanel rates unit />` («insuficiente» si `rates` null; `—` para anualizada null); en `ThemePage`, cada serie con ≥ 2 puntos carga `getSeries(id, range)` al cambiar el rango y muestra gráfica + `RatesPanel`; `Methodology` muestra definición, transformación, fuente con enlace, `queryUrl` y `seriesUrl` como enlaces.

- [ ] **Step 1: Write failing tests:** `RangeSelector` renderiza solo los rangos recibidos y marca el activo con `aria-pressed=true`; `RatesPanel` con `rates=null` muestra «insuficiente» y sin `NaN`; con `annualizedPct=null` muestra «—»; `Methodology` muestra el `queryUrl` como enlace con `href` exacto; `ThemePage` llama `getSeries` con el rango elegido al hacer clic (fetch mock).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar; el rango por defecto es `1a` si está disponible, si no `todo`. **Step 4:** PASS + build. **Step 5:** commit.

### Task 7: Front — gráficos (tendencia, barras, composición)

**Files:**
- Create: `paper/PieChart.tsx`, `paper/HBarChart.tsx`, `paper/YoYBars.tsx`, `components/DataTable.tsx`, `pages/Breakdowns.tsx`
- Modify: `paper/LineChart.tsx` (prop `trend?: {slope, intercept, r2}` calculado en cliente con MCO, línea punteada y leyenda), `pages/ThemePage.tsx`, `paper/paper.css`
- Test: `paper/charts.test.tsx`

**Interfaces:**
- Produces: `<PieChart items={{label,value,share}[]} />` (≤ 7 sectores, leyenda con %); `<HBarChart items />`; `<YoYBars points higherIsBetter />` (barras de variación %, color verde/rojo según `higherIsBetter`, neutro si null); `<DataTable headers rows />` dentro de `<details>`; `ols(points): {slope,intercept,r2}|null` en `format.ts`; `Breakdowns` con selector de dimensión y rango, mostrada en `/tema/seguridad`.

- [ ] **Step 1: Write failing tests:** `PieChart` con 0 items muestra «sin datos» y con 1 item (share 1) dibuja un círculo completo sin NaN en el path; `HBarChart` ordena descendente y su tabla tiene tantas filas como items; `YoYBars` con un valor previo 0 no produce `Infinity/NaN`; `ols` con < 5 puntos devuelve `null` y con 6 puntos de una recta exacta devuelve pendiente correcta y `r2≈1`; `LineChart` con `trend` dibuja una línea con clase `trend`; todos los gráficos tienen `<details>` con tabla.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar en SVG con la paleta de `paper.css`. **Step 4:** PASS + build. **Step 5:** commit.

### Task 8: Integración, revisión visual y despliegue

- [ ] **Step 1:** `cd web && npm run build`; copiar `dist` a `backend/src/main/resources/static`; `mvn -q -B -f backend/pom.xml package`; arrancar el jar con `--opencolombia.data-dir=<tmp>`; `curl localhost:8080/api/series/trm?range=6m`, `/api/methodology/trm`, `/api/breakdowns/homicidios?dim=sexo&range=1a` y comprobar datos reales y que `queryUrl` responde 200 al hacerle `curl`.
- [ ] **Step 2:** En el navegador integrado: tarjeta con «vs. <año>: <valor>», enlace a metodología, selector de rangos en una serie mensual y en una anual, tortas y barras en `/tema/seguridad`, vista 375 px sin scroll horizontal y sin errores de consola. Corregir lo que falle.
- [ ] **Step 3:** `git push origin main`; en el servidor `git pull --ff-only` y `sudo docker compose -f compose.tunnel.yaml up -d --build`; esperar `/api/health`.
- [ ] **Step 4: Verificación pública:** los tres hostnames devuelven 200; `/api/series/trm?range=6m` y `/api/methodology/trm` responden en producción; el bundle servido coincide con `web/dist`; `POST /api/refresh` sigue 14/14.
