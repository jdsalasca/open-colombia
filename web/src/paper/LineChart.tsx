import { DataTable } from '../components/DataTable'
import { fmt, type Ols } from '../format'
import type { Point } from '../types'

interface Props {
  points: Point[]
  unit: string
  height?: number
  /** undefined: no mostrar; null: no estimable (<5 datos); objeto: dibuja la recta MCO. */
  trend?: Ols | null
}

const YEAR_MS = 365.25 * 24 * 3600 * 1000

/** Línea con eje mín/máx, marcas de mínimo y máximo y, opcionalmente, la recta de tendencia. */
export function LineChart({ points, unit, height = 220, trend }: Props) {
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  if (sorted.length < 2) return <p className="card-empty">sin datos suficientes para graficar</p>
  const width = 640
  const padL = 48
  const padR = 14
  const padT = 14
  const padB = 28
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
  const first = sorted[0].date.slice(0, 4)
  const lastYear = sorted[sorted.length - 1].date.slice(0, 4)
  const iMin = vals.indexOf(min)
  const iMax = vals.indexOf(max)

  // la recta se recorta al rango visible del eje y
  const clampY = (v: number) => Math.min(max, Math.max(min, v))
  const tY0 = trend ? clampY(trend.intercept) : 0
  const tY1 = trend ? clampY(trend.intercept + trend.slope * (tSpan / YEAR_MS)) : 0

  return (
    <figure className="linechart-fig">
      <svg className="linechart" viewBox={`0 0 ${width} ${height}`} role="img"
           aria-label={`Serie de ${sorted.length} datos entre ${first} y ${lastYear}, de ${fmt(min)} a ${fmt(max)} ${unit}`}>
        <line x1={padL} x2={width - padR} y1={y(min)} y2={y(min)} className="axis" />
        <line x1={padL} x2={width - padR} y1={y(max)} y2={y(max)} className="axis" />
        <text x={padL - 6} y={y(max) + 4} textAnchor="end" className="tick">{fmt(max)}</text>
        <text x={padL - 6} y={y(min) + 4} textAnchor="end" className="tick">{fmt(min)}</text>
        <text x={padL} y={height - 8} className="tick">{first}</text>
        <text x={width - padR} y={height - 8} textAnchor="end" className="tick">{lastYear}</text>
        <path d={d} className="line" />
        {trend && (
          <line className="trend" x1={x(sorted[0].date)} y1={y(tY0)} x2={x(sorted[sorted.length - 1].date)} y2={y(tY1)} />
        )}
        {sorted.length <= 40 &&
          sorted.map((p) => <circle key={p.date} cx={x(p.date)} cy={y(p.value)} r={3} className="dot" />)}
        <circle cx={x(sorted[iMin].date)} cy={y(min)} r={6} className="dot-min" />
        <circle cx={x(sorted[iMax].date)} cy={y(max)} r={6} className="dot-max" />
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
