# Open Colombia

Cómo va Colombia, con datos abiertos oficiales, en estilo paper craft. Un botón **Actualizar datos**
re-consulta las fuentes; cada cifra muestra su fuente, fecha y periodicidad, y si una fuente falla se ve
«sin dato» (nunca un valor inventado). Incluye tendencias y propuestas con análisis econométrico ilustrativo.

Publicado en https://open-colombia.eridu.top

## Stack
- **Backend:** Java 25 + Spring Boot 4 (conectores, caché en disco, econometría con Apache Commons Math, API REST)
- **Front:** React + Vite + TypeScript (sin Next.js), gráficas SVG propias
- **Fuentes:** [datos.gov.co](https://www.datos.gov.co) (SODA) y [Banco Mundial](https://data.worldbank.org). Ver [docs/sources.md](docs/sources.md)

## Desarrollo
```bash
# backend (puerto 8080)
cd backend && ./mvnw spring-boot:run

# front con proxy a la API (puerto 5173)
cd web && npm install && npm run dev
```

Pruebas: `cd backend && ./mvnw test` y `cd web && npm test`.

## Producción
```bash
docker compose -f compose.tunnel.yaml up -d --build   # publica 127.0.0.1:5174
```

## API
`GET /api/indicators` · `GET /api/themes/{id}` · `GET /api/insights` · `POST /api/refresh` (1 por minuto) · `GET /api/health`

## Sobre las propuestas
Son análisis ilustrativos: tendencias de ventana reciente y asociaciones estadísticas simples, contrastadas
con literatura económica. No son política pública oficial ni prueban causalidad; cada bloque declara su n y R²,
y marca como poco confiable una relación que solo comparte tendencia.
