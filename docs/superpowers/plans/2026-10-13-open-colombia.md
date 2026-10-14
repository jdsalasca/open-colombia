# Open Colombia Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App pública que muestra cómo va Colombia con datos abiertos, estilo paper craft, botón Actualizar y propuestas econométricas, desplegada en `https://open-colombia.eridu.top`.

**Architecture:** Spring Boot (Java) trae datos de fuentes abiertas, los normaliza a `Series`, los cachea en disco, calcula estadística/econometría y expone una API JSON. Un front React + Vite + TypeScript la consume. Un Dockerfile multi-stage empaqueta todo en un contenedor tras el túnel cloudflared existente.

**Tech Stack:** Java (JDK vía SDKMAN, default 25), Spring Boot (versión compatible con ese JDK, fijada en Task 1), Maven wrapper, Apache Commons Math 3, JUnit 5/MockMvc; React 18, Vite, TypeScript, Vitest + Testing Library; Docker, cloudflared.

**Spec:** `docs/superpowers/specs/2026-10-13-open-colombia-design.md`

## Global Constraints

- Backend Java + Spring Boot; front React + Vite + TypeScript; **sin Next.js**.
- Repo público `jdsalasca/open-colombia`, monorepo con `backend/` y `web/`; git con la cuenta `jdsalasca` existente, nunca crear cuentas.
- 6 temas v1 con ids: `economia`, `empleo`, `pobreza`, `seguridad`, `salud-educacion`, `ambiente-energia`.
- Cada cifra lleva fuente, fecha y periodicidad; si una fuente falla se muestra "sin dato", nunca valores inventados ni rellenados.
- Refresco: `POST /api/refresh` limitado a 1 por minuto global; fallo parcial no borra datos previos.
- Sin secretos en el repo; ninguna fuente exige llave en v1.
- Propuestas = análisis ilustrativo con supuestos, n y R² explícitos, no política oficial.
- Paper craft con gráficas propias en SVG (sin librería de charts); accesible AA, teclado, `prefers-reduced-motion`, responsive.
- Despliegue: `~/sitios/open-colombia` en el servidor, puerto `5174`→8080, regla de ingress antes del catch-all 404, respaldo previo de `~/.cloudflared/config.yml`; `hotel.eridu.top` y `bogota-mitos.eridu.top` deben seguir respondiendo.

## Review Focus

- Fuente caída o con timeout durante `refresh`: la caché previa se conserva y el reporte marca ese id como `ok=false`.
- Serie vacía o de 1–2 puntos: econometría devuelve "datos insuficientes", no NaN ni excepción.
- Valores nulos/`"."`/vacíos en la respuesta de la fuente: se descartan, no rompen el parseo.
- Doble clic o spam en Actualizar: el segundo llamado recibe 429 y la UI lo muestra sin romperse.
- Caché inexistente en primer arranque: la API responde 200 con series vacías y la UI muestra "sin dato".

## File Structure

```
backend/pom.xml
backend/src/main/java/top/eridu/opencolombia/
  OpenColombiaApplication.java
  model/{Point,Series,Theme,Insight}.java       records de dominio
  source/{SourceConnector,WorldBankConnector,SodaConnector,SourceCatalog}.java
  cache/SeriesStore.java                        JSON en disco
  refresh/{RefreshService,RefreshReport,RefreshScheduler}.java
  analytics/{Stats,OlsResult}.java
  insight/InsightService.java
  api/{IndicatorController,RefreshController,HealthController}.java
backend/src/test/java/...                       espejo por paquete
web/package.json, vite.config.ts, tsconfig.json
web/src/{main.tsx,App.tsx,api.ts,types.ts}
web/src/paper/{PaperCard,PaperSheet,Sparkline,LineChart,Gauge,paper.css}
web/src/pages/{Home,ThemePage,Proposals}.tsx
web/src/components/{RefreshButton,SourceChip}.tsx
Dockerfile, compose.tunnel.yaml, .gitignore, README.md, docs/sources.md
```

---

### Task 1: Esqueleto backend y modelo de dominio

**Files:**
- Create: `backend/pom.xml`, `OpenColombiaApplication.java`, `model/Point.java`, `model/Series.java`, `model/Theme.java`
- Test: `backend/src/test/java/top/eridu/opencolombia/model/SeriesTest.java`

**Interfaces:**
- Produces: `record Point(LocalDate date, double value)`; `record Series(String id, String theme, String name, String unit, String source, String sourceUrl, String periodicity, List<Point> points, Instant fetchedAt)` con `Optional<Point> latest()` y `Series withPoints(List<Point>)`; `enum Theme { ECONOMIA("economia"), ... }` con `String id()` y `static Optional<Theme> byId(String)`.

- [ ] **Step 1:** Verificar el JDK (`java -version`, debe ser el default SDKMAN 25) y buscar con `curl https://api.github.com/repos/spring-projects/spring-boot/releases/latest` la última versión estable de Spring Boot; confirmar en su documentación que soporta ese JDK, y fijarla en `pom.xml` (starter-web, starter-test, commons-math3 3.6.1). Generar `mvnw` con `mvn -N wrapper:wrapper` (Maven vía winget solo si falta).
- [ ] **Step 2: Write failing test** `SeriesTest`: `latest()` devuelve el punto de fecha máxima aunque la lista llegue desordenada; `latest()` de lista vacía es `Optional.empty()`; `Theme.byId("economia")` presente y `byId("x")` vacío.
- [ ] **Step 3:** Run `./mvnw -q test -Dtest=SeriesTest` → FAIL (clases inexistentes).
- [ ] **Step 4:** Implementar los records y el enum con las firmas arriba.
- [ ] **Step 5:** Run `./mvnw -q test` → PASS; `./mvnw -q spring-boot:run` arranca y `curl localhost:8080/actuator/health` no se exige aún.
- [ ] **Step 6:** `git init`, `.gitignore` (target, node_modules, dist, data/), commit `chore: backend skeleton and domain model`.

### Task 2: Caché en disco (SeriesStore)

**Files:**
- Create: `cache/SeriesStore.java`
- Test: `cache/SeriesStoreTest.java`

**Interfaces:**
- Consumes: `Series`.
- Produces: `class SeriesStore(Path dir)` con `void put(Series)`, `Optional<Series> get(String id)`, `List<Series> all()`; dir configurable por `opencolombia.data-dir` (default `./data`).

- [ ] **Step 1: Write failing tests** (con `@TempDir`): put→get devuelve la misma serie; `all()` sobre directorio vacío o inexistente devuelve `[]` (Review Focus: primer arranque); un archivo JSON corrupto se ignora en `all()` y `get` devuelve vacío sin lanzar.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar con Jackson, un archivo `<id>.json` por serie, escritura atómica (tmp + move). **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 3: Sondeo de fuentes y catálogo verificado

**Files:**
- Create: `docs/sources.md`, `source/SourceCatalog.java`
- Test: `source/SourceCatalogTest.java`

**Interfaces:**
- Produces: `record IndicatorSpec(String id, Theme theme, String name, String unit, String periodicity, Kind kind, String ref, String valueField, String dateField)` donde `Kind` ∈ `WORLD_BANK|SODA`; `SourceCatalog.specs(): List<IndicatorSpec>`.

- [ ] **Step 1:** Con `curl`, verificar contra las APIs reales (no de memoria): Banco Mundial `https://api.worldbank.org/v2/country/COL/indicator/<code>?format=json&per_page=100` para candidatos `NY.GDP.MKTP.KD.ZG`, `FP.CPI.TOTL.ZG`, `SL.UEM.TOTL.ZS`, `SL.EMP.VULN.ZS`, `SI.POV.GINI`, `SI.POV.NAHC`, `VC.IHR.PSRC.P5`, `SP.DYN.IMRT.IN`, `AG.LND.FRST.ZS`, `EG.USE.ELEC.KH.PC`; y en datos.gov.co (SODA, `https://www.datos.gov.co/resource/<id>.json?$limit=5`) buscar con el catálogo de datos.gov.co datasets de TRM, homicidios, desempleo mensual DANE y cobertura educativa. Registrar en `docs/sources.md` cada indicador con URL, periodicidad, último dato y si responde. **Solo los verificados entran al catálogo.** Mínimo 2 indicadores por tema; si un tema no alcanza el mínimo, reportarlo al usuario antes de seguir.
- [ ] **Step 2: Write failing test:** el catálogo cubre los 6 temas con ≥1 indicador, ids únicos, ningún `ref` vacío.
- [ ] **Step 3:** Run → FAIL. **Step 4:** Implementar `SourceCatalog` con la lista verificada. **Step 5:** Run → PASS. **Step 6:** Commit.

### Task 4: Conectores World Bank y SODA

**Files:**
- Create: `source/SourceConnector.java`, `source/WorldBankConnector.java`, `source/SodaConnector.java`
- Test: `source/WorldBankConnectorTest.java`, `source/SodaConnectorTest.java`, fixtures en `src/test/resources/fixtures/`

**Interfaces:**
- Consumes: `IndicatorSpec`, `Series`.
- Produces: `interface SourceConnector { boolean supports(IndicatorSpec s); Series fetch(IndicatorSpec s) throws SourceException; }`; `class SourceException extends Exception`. Constructores reciben `RestClient` para inyectar `MockRestServiceServer`.

- [ ] **Step 1: Write failing tests** con fixtures grabadas de la respuesta real (de Task 3): parseo correcto a `Point` ordenados ascendente; entradas con `value: null`, `"."` o vacías se descartan (Review Focus); HTTP 500 y timeout lanzan `SourceException`; respuesta con 0 puntos devuelve serie vacía, no excepción.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar; `RestClient` con timeouts de conexión/lectura de 8 s y User-Agent `open-colombia/1.0`. **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 5: Servicio de refresco y scheduler

**Files:**
- Create: `refresh/RefreshService.java`, `RefreshReport.java`, `RefreshScheduler.java`
- Test: `refresh/RefreshServiceTest.java`

**Interfaces:**
- Consumes: `List<SourceConnector>`, `SourceCatalog`, `SeriesStore`.
- Produces: `record SourceResult(String id, boolean ok, String error)`; `record RefreshReport(Instant at, List<SourceResult> results)`; `RefreshReport RefreshService.refreshAll()` (paralelo con virtual threads, 1 reintento por fuente); `RefreshScheduler` con `@Scheduled(cron = "0 0 6 * * *")`.

- [ ] **Step 1: Write failing tests** con conectores falsos: todo ok → store contiene todas; un conector lanza `SourceException` → su serie previa sigue intacta en el store y su `SourceResult.ok=false` (Review Focus); un fallo no impide las demás; un fallo transitorio se recupera con el reintento.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar. **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 6: Econometría (Stats)

**Files:**
- Create: `analytics/Stats.java`, `analytics/OlsResult.java`
- Test: `analytics/StatsTest.java`

**Interfaces:**
- Consumes: `Series`.
- Produces: `record OlsResult(double slope, double intercept, double r2, int n)`; `Optional<OlsResult> Stats.trend(Series s)` (x = años desde primer punto); `Optional<Double> Stats.yoyChange(Series s)`; `Optional<Double> Stats.correlation(Series a, Series b)` (alinea por año, mínimo 5 pares); `Optional<OlsResult> Stats.regress(Series x, Series y)`; `Optional<Forecast> Stats.forecast(Series s, int yearsAhead)` con `record Forecast(double value, double low, double high)` (intervalo 95%). Todas devuelven `Optional.empty()` si n < 5.

- [ ] **Step 1: Write failing tests** con resultados conocidos: y = 2x+1 sobre 6 puntos → slope 2, intercept 1, r2 1.0; correlación de series perfectamente inversas = -1; series de 3 puntos → `Optional.empty()` (Review Focus); serie constante → r2 definido sin NaN; `forecast` a 1 año sobre la recta exacta = valor esperado con intervalo de ancho ~0.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar con `SimpleRegression` y `PearsonsCorrelation` de Commons Math. **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 7: Insights y propuestas

**Files:**
- Create: `model/Insight.java`, `insight/InsightService.java`, `src/main/resources/insights.yml` (plantillas y referencias)
- Test: `insight/InsightServiceTest.java`

**Interfaces:**
- Consumes: `SeriesStore`, `Stats`.
- Produces: `record Insight(String themeId, String title, String finding, String literature, String proposal, Confidence confidence)`; `record Confidence(int n, Double r2, String level)` con level ∈ `alta|media|baja|insuficiente`; `List<Insight> InsightService.insights()`.

- [ ] **Step 1: Write failing tests:** con series sintéticas de desempleo↔pobreza correlacionadas, aparece un insight cuyo `finding` contiene el slope y el R² calculados; con n<5 el insight sale con `level="insuficiente"` y la propuesta aclara que no hay evidencia suficiente; sin series en el store devuelve lista vacía, no excepción.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar; nivel: n≥15 y r2≥0.6 alta, n≥8 media, resto baja. `insights.yml` define por tema: pares de indicadores a regresar, texto de literatura citada (Okun, Phillips, crecimiento inclusivo/Kuznets) y plantilla de propuesta con supuestos. **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 8: API REST

**Files:**
- Create: `api/IndicatorController.java`, `RefreshController.java`, `HealthController.java`, `api/RateLimiter.java`
- Test: `api/ApiTest.java` (MockMvc)

**Interfaces:**
- Produces: `GET /api/indicators` → `{themes:[{id,name,series:[Series]}], updatedAt}`; `GET /api/themes/{id}` (404 si desconocido); `GET /api/insights` → `List<Insight>`; `POST /api/refresh` → `RefreshReport` o 429 con `Retry-After`; `GET /api/health` → `{status:"ok", series:<int>, updatedAt}`. CORS solo en perfil `dev` (origin `http://localhost:5173`).

- [ ] **Step 1: Write failing tests:** indicators con store vacío → 200 y temas con `series:[]` (Review Focus); dos `POST /api/refresh` seguidos → 200 y luego 429; theme inexistente → 404; health → 200.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implementar; `RateLimiter` global 1/minuto con reloj inyectable. **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 9: Front — esqueleto, API cliente y sistema paper craft

**Files:**
- Create: `web/` (Vite react-ts), `src/types.ts`, `src/api.ts`, `src/paper/{PaperSheet,PaperCard,Sparkline,LineChart,Gauge}.tsx`, `src/paper/paper.css`
- Test: `src/api.test.ts`, `src/paper/PaperCard.test.tsx`, `src/paper/Sparkline.test.tsx`

**Interfaces:**
- Consumes: contrato JSON de Task 8.
- Produces: tipos TS espejo (`Point`, `Series`, `Insight`, `RefreshReport`); `getIndicators(): Promise<IndicatorsResponse>`, `getInsights()`, `refresh(): Promise<RefreshReport | {rateLimited: true; retryAfter: number}>`; `<PaperCard title value unit delta source date series? />`; `<Sparkline points />`; `<LineChart points unit />`; `<Gauge value min max />`.

- [ ] **Step 1: Write failing tests:** `refresh()` mapea 429 a `{rateLimited:true,retryAfter}`; `PaperCard` con serie vacía muestra "sin dato" y no el número; `Sparkline` con 0 o 1 punto renderiza sin lanzar y sin `NaN` en el path (Review Focus).
- [ ] **Step 2:** `npm run test` → FAIL. **Step 3:** Implementar; proxy de Vite `/api`→`localhost:8080`; paper craft con capas (sombras apiladas, bordes irregulares por SVG `clip-path`, textura cartulina CSS, plegado con gradiente), paleta cálida con acentos de la bandera, `prefers-reduced-motion` desactiva animaciones. **Step 4:** tests PASS y `npm run build` sin errores TS. **Step 5:** Commit.

### Task 10: Front — páginas, navegación y botón Actualizar

**Files:**
- Create: `src/App.tsx`, `src/main.tsx`, `src/pages/{Home,ThemePage,Proposals}.tsx`, `src/components/{RefreshButton,SourceChip}.tsx`
- Test: `src/components/RefreshButton.test.tsx`, `src/pages/ThemePage.test.tsx`

**Interfaces:**
- Consumes: `api.ts` y componentes paper de Task 9.
- Produces: rutas `/` (tablero con una hoja por tema), `/tema/:id`, `/propuestas`; `<RefreshButton onDone />`.

- [ ] **Step 1: Write failing tests:** pulsar el botón llama `refresh()` una vez y queda deshabilitado mientras corre (el doble clic no dispara dos llamadas); respuesta rateLimited muestra el mensaje con segundos y rehabilita; `ThemePage` de tema sin series muestra "sin dato" por indicador; `Proposals` muestra n y R² junto a cada propuesta.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar con React Router; cada tarjeta lleva `SourceChip` (fuente, fecha, periodicidad); foco visible y orden de tab lógico. **Step 4:** `npm run test` y `npm run build` PASS. **Step 5:** Commit.

### Task 11: Integración local y verificación visual

**Files:**
- Modify: `backend` para servir `web/dist` como estático (copiado a `static/` por el build); `README.md`

- [ ] **Step 1:** `npm run build` en `web`, copiar `dist` a `backend/src/main/resources/static`, `./mvnw -q package`.
- [ ] **Step 2:** `java -jar backend/target/*.jar`; `curl -s -X POST localhost:8080/api/refresh` y comprobar que el reporte lista cada indicador; `curl localhost:8080/api/indicators` trae puntos reales.
- [ ] **Step 3:** Abrir `http://localhost:8080` en el navegador integrado: pulsar Actualizar, recorrer los 6 temas y `/propuestas`, revisar vista móvil (375 px) y consola sin errores. Corregir lo que falle antes de seguir.
- [ ] **Step 4:** Commit.

### Task 12: Contenedor y repo público

**Files:**
- Create: `Dockerfile`, `compose.tunnel.yaml`, `.dockerignore`

**Interfaces:**
- Produces: imagen que expone 8080, volumen `/data` para `opencolombia.data-dir`, healthcheck a `/api/health`; compose publica `5174:8080`.

- [ ] **Step 1:** Dockerfile multi-stage: node (build web) → maven/JDK (build jar con `static/` ya copiado) → JRE slim con usuario no root. **Step 2:** `docker compose -f compose.tunnel.yaml up -d --build` local y verificar `curl localhost:5174/api/health` → 200 y `/` sirve la UI. **Step 3:** Confirmar con el usuario, y solo entonces `gh repo create jdsalasca/open-colombia --public --source . --push` (verificar antes que `gh auth status` use `jdsalasca` y que no haya secretos con `git grep -iE "key|token|secret"`). **Step 4:** Commit y push.

### Task 13: Despliegue en el servidor

- [ ] **Step 1:** `ssh <usuario>@<servidor>`; `cp ~/.cloudflared/config.yml ~/.cloudflared/config.yml.bak-$(date +%F)`. Anotar el estado actual: `curl -s -o /dev/null -w "%{http_code}"` de `hotel.eridu.top` y `bogota-mitos.eridu.top`.
- [ ] **Step 2:** `git clone https://github.com/jdsalasca/open-colombia ~/sitios/open-colombia`; `sudo docker compose -f compose.tunnel.yaml up -d --build`; `curl localhost:5174/api/health` → 200 en el servidor.
- [ ] **Step 3:** Añadir en `config.yml` antes del `http_status:404` el ingress `hostname: open-colombia.eridu.top` → `service: http://localhost:5174`; `cloudflared tunnel ingress validate`; `sudo systemctl restart cloudflared`; `cloudflared tunnel route dns <tunnel-id> open-colombia.eridu.top` (si falla por permisos, indicar al usuario crear el CNAME en el panel).
- [ ] **Step 4: Verificación:** `https://open-colombia.eridu.top/api/health` → 200; el bundle `assets/index-*.js` servido coincide con `web/dist`; `hotel.eridu.top` y `bogota-mitos.eridu.top` devuelven los mismos códigos que en el Step 1. Si alguno cambió, restaurar `config.yml.bak-*` y reiniciar cloudflared.
- [ ] **Step 5:** Pulsar Actualizar en la URL pública y confirmar datos reales; reportar al usuario la URL y el repo.
