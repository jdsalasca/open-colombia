# Open Colombia v2 — diseño

Fecha: 2026-10-14 · Estado: pendiente de revisión · Base: v1 desplegada (ver `2026-10-13-open-colombia-design.md`)

## 1. Objetivo
Hacer la app más transparente y explorable: que cada cifra diga con qué se compara y cómo se calcula,
que las series se puedan ver por ventanas de tiempo con sus tasas, y que haya más tipos de gráfico
(tendencia, barras, tortas) donde los datos lo permitan.

Éxito: (1) ninguna tarjeta dice «dato anterior» sin mostrarlo; (2) desde cualquier cifra se llega a
una página que muestra la fuente, la transformación y la URL exacta de la API; (3) cada serie tiene
selector de rango con tasas calculadas; (4) homicidios muestra composición por categorías.

## 2. Decisiones ya tomadas
- Mismo stack (Spring Boot + React/Vite/TS), sin librerías nuevas de gráficos; SVG propio, estilo paper craft.
- Rangos en dos modos: series **mensuales** → 3m, 6m, 1a, 2a, Todo; series **anuales** → 5a, 10a, Todo.
  La UI solo ofrece los rangos válidos de cada serie.
- «Recargar por periodo» = cambiar la ventana de la vista sobre datos ya guardados; no vuelve a consultar
  las fuentes (eso sigue siendo el botón Actualizar).
- Tortas solo donde hay categorías reales (homicidios). No se inventan composiciones.
- Fuera de alcance: nuevas fuentes de alta frecuencia, nuevas librerías, composición de PIB/desempleo.

## 3. Claridad (A)
**Dato anterior explícito.** La tarjeta muestra «▲ 1,15 vs. 2024: 3,99 %» (valor y periodo anteriores).
Cálculo en el front sobre la serie recibida; sin cambios de backend.

**Metodología por indicador.** Enlace «¿cómo se calcula?» en tarjetas y gráficas → `/metodologia/:id`.
- `IndicatorSpec` gana `description` y `method` (texto de transformación, p. ej. «promedio mensual de la
  TRM diaria»).
- `GET /api/methodology/{id}` → `{id, name, description, method, source, sourceUrl, queryUrl, seriesUrl,
  periodicity, unit}`. `queryUrl` es la URL exacta de la API upstream que consulta el conector (debe ser la
  misma que usa `fetch`, construida por una función compartida para que no diverjan); `seriesUrl` es
  `/api/series/{id}`.
- `/metodologia` (índice) define cada tasa y estadístico: variación, anualizada, promedio, volatilidad,
  tendencia, R², intervalo, correlación de variaciones.

## 4. Rangos y tasas (B)
`GET /api/series/{id}?range=<r>` →
```
{ series: Series (puntos del rango), range, availableRanges: [..],
  rates: { n, from, to, change, changePct, annualizedPct|null, mean, min:{date,value},
           max:{date,value}, stdDev } | null }
```
- Rango inválido para la periodicidad → 400 con `availableRanges`. Serie desconocida → 404.
- Rango = puntos con fecha > último dato − duración. Todo = sin recorte.
- `rates` es `null` con menos de 2 puntos (no NaN). `annualizedPct` solo si el rango abarca ≥ 12 meses
  y el valor inicial es > 0; fórmula `((v_fin / v_ini)^(365.25/días) − 1) × 100`.
- Se calculan en Java en `analytics/Rates` con pruebas de valores conocidos.
- Front: selector de rango (chips) por gráfica, con el rango activo en `aria-pressed`; muestra el bloque
  de tasas junto a la gráfica.

## 5. Gráficos (C)
- **Tendencia:** línea punteada MCO sobre el rango visible, con pendiente y R² en la leyenda; marcas de
  mín y máx. Con menos de 5 puntos no se dibuja tendencia (se indica).
- **Barras de variación interanual** para series anuales. Cada indicador declara `higherIsBetter`
  (true/false/null); el color de la barra sigue esa semántica y es neutro si es null.
- **Composición de homicidios** (`GET /api/breakdowns/homicidios?dim=<sexo|zona|arma|departamento>&range=<r>`):
  consulta SODA agregada (`$group` por la columna, `$where` por rango), caché en memoria 1 h por
  `dim+range`. Tortas para `sexo`, `zona`, `arma` (≤ 6 categorías + «otros»); barras horizontales para
  `departamento` (top 8 + «otros»). Dimensión inválida → 400.
- Todas las gráficas llevan una tabla de datos accesible en `<details>` y respetan
  `prefers-reduced-motion`.

## 6. Pruebas
- Backend (JUnit): `Rates` (valores conocidos, <2 puntos, anualizada solo ≥12 m, inicial ≤ 0),
  recorte por rango, validación de rangos por periodicidad (400), `queryUrl` igual a la URL realmente
  consultada, breakdown (agrupa, top-N + «otros», dimensión inválida, caché).
- Front (Vitest): tarjeta muestra valor y periodo anteriores; selector solo ofrece rangos válidos;
  torta con una sola categoría y con 0 categorías no produce NaN; barras con valores negativos.

## 7. Riesgos
- Campos de categoría de `m8fd-ahd9` distintos a lo esperado → el plan empieza verificando la API real.
- Rangos cortos sobre pocos puntos → tasas «insuficiente» en vez de números espurios.
- Doble fuente de verdad para `queryUrl` → una sola función la construye y la usan conector y metodología.
