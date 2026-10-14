# Open Colombia — diseño

Fecha: 2026-10-13 · Estado: pendiente de revisión

## 1. Objetivo
App web pública que muestra "cómo va Colombia" con datos abiertos oficiales, en estilo
paper craft, con un botón **Actualizar** que re-consulta las fuentes, y una sección de
análisis econométrico con propuestas de mejora. Publicada en
`https://open-colombia.eridu.top`, repo público, backend Java + Spring Boot, front React + Vite + TypeScript (sin Next.js).

Éxito: abrir la URL, pulsar Actualizar, ver cifras reales con fuente y fecha, navegar por
6 temas y leer un diagnóstico con propuestas respaldadas por los datos.

## 2. Decisiones ya tomadas
- Público objetivo: compartir/publicar (ciudadanía general, portafolio).
- Backend: Java + Spring Boot (JDK vía SDKMAN; versión de Boot compatible con el JDK, a verificar en el plan). Front: React + Vite + TypeScript. Sin Next.js.
- Repo público `jdsalasca/open-colombia` (monorepo).
- Un solo contenedor: Spring Boot sirve la API y el build estático de Vite.
- 6 temas v1: Economía, Empleo, Pobreza y desigualdad, Seguridad, Salud y educación,
  Ambiente y energía.
- Honestidad de datos: cada cifra lleva fuente, fecha y periodicidad. Si una fuente falla,
  se muestra "sin dato"; nunca se inventan ni se rellenan valores.
- Las propuestas son análisis ilustrativo con supuestos explícitos, no política oficial.

## 3. Arquitectura
```
open-colombia/
  backend/   Spring Boot (Maven wrapper): conectores, caché, econometría, API
  web/       React + Vite + TypeScript: UI paper craft
  Dockerfile (multi-stage: build web -> build jar -> runtime), compose.tunnel.yaml
```
El build de `web` se copia a `static/` del jar; el contenedor corre un único proceso.
Los tipos del front se mantienen a mano contra el contrato JSON de la API (DTOs Java =
fuente de verdad, documentados con OpenAPI).

### Backend (Java / Spring Boot)
- **Conectores** (`SourceConnector`, un bean por fuente, con `RestClient`): devuelve 
  `Series` normalizada `{id, nombre, unidad, fuente, url, periodicidad, puntos[{fecha, valor}]}`.
  Fuentes: API SODA de datos.gov.co (DANE, Policía, Minsalud, etc.), API del Banco
  Mundial (respaldo anual: PIB, Gini, desempleo, inflación), Banco de la República / datos
  .gov.co para TRM y tasas. Los IDs exactos de datasets se verifican contra la API real
  como primera tarea del plan; un conector sin dataset verificado no se incluye.
- **Caché**: JSON en disco (volumen Docker) por indicador, con `actualizadoEn`. La API
  siempre responde desde caché; el refresco es explícito.
- **Refresco**: `POST /api/refresh` (limitado a 1 por minuto, global) re-consulta todas
  las fuentes en paralelo con timeout y reintento; un fallo parcial no borra datos
  previos. Job diario automático (`@Scheduled`).
- **Econometría** (Java, con Apache Commons Math para MCO/correlación; sin ML): tendencia
  (regresión lineal/MCO), variación interanual, correlaciones y regresión simple entre
  pares plausibles (p. ej. desempleo↔pobreza, inflación↔tasa), proyección de corto plazo
  con intervalo. Cada resultado reporta n, R² y advertencia si n es pequeño.
- **API**: `GET /api/indicators`, `GET /api/themes/:id`, `GET /api/insights`,
  `POST /api/refresh`, `GET /api/health`.

### Front
- Navegación por temas (tablero de inicio + una página por tema + página "Qué hacer").
- Estilo paper craft: capas de papel con sombras, bordes irregulares (SVG), textura de
  cartulina, plegados, paleta cálida con la bandera como acento. Gráficas propias en SVG
  (línea, barras, termómetro, medidor), sin librería de charts.
- Cada tarjeta: cifra grande, variación, sparkline, chip de fuente/fecha.
- Botón Actualizar con estado de progreso y resultado por fuente.
- Accesible: contraste AA, navegable por teclado, `prefers-reduced-motion`, responsive.

## 4. Sección de propuestas
Para cada tema, un bloque "qué dicen los datos → qué dice la literatura → propuesta",
generado a partir de los resultados de `analytics` con plantillas redactadas a mano y
referencias citadas (literatura económica estándar: Okun, Phillips, crecimiento
inclusivo). El texto indica los supuestos y el nivel de confianza (n, R²).

## 5. Despliegue (servidor el servidor)
Siguiendo `server/servidor-hotel-os.md`:
1. `~/sitios/open-colombia`, clon del repo público; `compose.tunnel.yaml` publica
   `5174→3000` con volumen para la caché.
2. Respaldar `~/.cloudflared/config.yml`, añadir ingress
   `open-colombia.eridu.top → http://localhost:5174` **antes** del catch-all 404,
   validar con `cloudflared tunnel ingress validate`, reiniciar el servicio.
3. Registrar el DNS del hostname en el túnel.
4. Verificar: `hotel.eridu.top` y `bogota-mitos.eridu.top` siguen respondiendo;
   `open-colombia.eridu.top` y `/api/health` responden; bundle servido = build.
Sin secretos en el repo; ninguna fuente requiere llave en v1 (si alguna la pidiera,
se omite o se pide al usuario).

## 6. Pruebas
- Backend (JUnit 5): normalizadores de cada conector con respuestas grabadas, econometría
  con casos de resultado conocido, controladores con MockMvc.
- Front (Vitest + Testing Library): componentes clave y cliente de la API.
- Prueba manual del flujo en navegador (Actualizar, navegación, móvil) antes de desplegar.

## 7. Fuera de alcance v1
Cuentas de usuario, comparación entre países, mapas por municipio, predicciones
"inteligentes" con ML, datos en tiempo real intradía.

## 8. Riesgos
- Cambios o caídas en APIs públicas → caché + "sin dato" + reintento.
- Tocar el túnel compartido → respaldo de config y verificación de los 3 hostnames.
- Series cortas en econometría → se declara n y se evita sobre-interpretar.
