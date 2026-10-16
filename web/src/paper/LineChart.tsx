import { useId } from 'react'
import { DataTable } from '../components/DataTable'
import { fmt, periodLabel, type Ols } from '../format'
import type { Point } from '../types'

interface Props {
  points: Point[]
  unit: string
  height?: number
  /** undefined: no mostrar; null: no estimable (<5 datos); objeto: dibuja la recta MCO. */
  trend?: Ols | null
  /** Formato de las etiquetas del eje x; por defecto «mes año» en series cortas y «año» en largas. */
  xLabel?: (date: string) => string
}

const YEAR_MS = 365.25 * 24 * 3600 * 1000
const MONTH_MS = YEAR_MS / 12

/** Línea compacta con marcas de mínimo (▼) y máximo (▲) rotuladas y, opcionalmente, la recta de tendencia. */
export function LineChart({ points, unit, height = 170, trend, xLabel }: Props) {
  const clipId = useId()
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  if (sorted.length < 2) return <p className="card-empty">sin datos suficientes para graficar</p>

  const width = 640
  const padL = 48
  const padR = 14
  const padT = 18
  const padB = 26
  const vals = sorted.map((p) => p.value)
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const span = max - min || 1
  const t0 = Date.parse(sorted[0].date)
  const tN = Date.parse(sorted[sorted.length - 1].date)
  const tSpan = tN - t0 || 1
  const x = (date: string) => padL + ((Date.parse(date) - t0) / tSpan) * (width - padL - padR)
  const y = (v: number) => height - padB - ((v - min) / span) * (height - padT - padB)
  const d = sorted.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.date).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')

  const shortSeries = tSpan <= 24 * MONTH_MS
  // datos casi diarios (mediana de separación <= 2 días): el eje muestra el día; si no, mes o año
  const gaps = sorted.slice(1).map((p, i) => (Date.parse(p.date) - Date.parse(sorted[i].date)) / 86400000).sort((a, b) => a - b)
  const dailyLike = gaps.length > 0 && gaps[Math.floor(gaps.length / 2)] <= 2 && tSpan <= 400 * 86400000
  const label = xLabel ?? ((date: string) =>
    dailyLike ? periodLabel(date, 'diaria') : shortSeries ? periodLabel(date, 'mensual') : date.slice(0, 4))
  const iMin = vals.indexOf(min)
  const iMax = vals.indexOf(max)
  const tri = (cx: number, cy: number, up: boolean) =>
    up ? `M${cx - 6},${cy + 5} L${cx + 6},${cy + 5} L${cx},${cy - 6} Z` : `M${cx - 6},${cy - 5} L${cx + 6},${cy - 5} L${cx},${cy + 6} Z`
  const minX = x(sorted[iMin].date)
  const maxX = x(sorted[iMax].date)

  return (
    <figure className="linechart-fig">
      <svg className="linechart" viewBox={`0 0 ${width} ${height}`} role="img"
           aria-label={`Serie de ${sorted.length} datos entre ${label(sorted[0].date)} y ${label(sorted[sorted.length - 1].date)}, de ${fmt(min)} a ${fmt(max)} ${unit}`}>
        <defs>
          <clipPath id={clipId}>
            <rect x={padL} y={padT - 4} width={width - padL - padR} height={height - padT - padB + 8} />
          </clipPath>
        </defs>
        <line x1={padL} x2={width - padR} y1={y(min)} y2={y(min)} className="axis" />
        <line x1={padL} x2={width - padR} y1={y(max)} y2={y(max)} className="axis" />
        <text x={padL - 6} y={y(max) + 4} textAnchor="end" className="tick">{fmt(max)}</text>
        <text x={padL - 6} y={y(min) + 4} textAnchor="end" className="tick">{fmt(min)}</text>
        <text x={padL} y={height - 8} className="tick">{label(sorted[0].date)}</text>
        <text x={width - padR} y={height - 8} textAnchor="end" className="tick">{label(sorted[sorted.length - 1].date)}</text>
        <path d={d} className="line" />
        {trend && (
          <line
            className="trend"
            clipPath={`url(#${clipId})`}
            x1={x(sorted[0].date)} y1={y(trend.intercept)}
            x2={x(sorted[sorted.length - 1].date)} y2={y(trend.intercept + trend.slope * (tSpan / YEAR_MS))}
          />
        )}
        {sorted.length <= 40 &&
          sorted.map((p) => <circle key={p.date} cx={x(p.date)} cy={y(p.value)} r={3} className="dot" />)}
        <path className="mark mark-min" d={tri(minX, y(min), false)} />
        <path className="mark mark-max" d={tri(maxX, y(max), true)} />
        <text className="mark-text" x={Math.min(Math.max(minX, padL + 24), width - padR - 24)} y={y(min) - 10}
              textAnchor="middle">▼ mín</text>
        <text className="mark-text" x={Math.min(Math.max(maxX, padL + 24), width - padR - 24)} y={y(max) - 10}
              textAnchor="middle">▲ máx</text>
      </svg>
      {trend === null && <p className="chip">Tendencia no estimada: se necesitan al menos 5 datos.</p>}
      {trend && (
        <p className="chip">
          Tendencia (línea punteada): {trend.slope >= 0 ? '+' : ''}{fmt(trend.slope)} {unit} por año · R² = {fmt(trend.r2)}
        </p>
      )}
      <DataTable
        caption="Serie"
        headers={['Fecha', `Valor (${unit})`]}
        rows={sorted.map((p) => [p.date, fmt(p.value)])}
      />
    </figure>
  )
}
