import { DataTable } from '../components/DataTable'
import { fmt } from '../format'
import type { Point } from '../types'

interface Props {
  points: Point[]
  unit: string
  /** Cuántos días calendario terminan en el último dato. */
  days?: number
  label?: string
}

const DAY = 86400000

function iso(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10)
}

/**
 * Tira de calor diaria tipo calendario (columnas = semanas). Intensidad en 4 pasos de un solo matiz; los
 * días sin dato son huecos con trama, nunca un color de la escala. Cada celda y la tabla dicen su valor.
 */
export function HeatStrip({ points, unit, days = 90, label = 'Serie diaria' }: Props) {
  if (points.length === 0) return <p className="card-empty">sin datos para dibujar la tira diaria</p>
  const byDate = new Map(points.map((p) => [p.date.slice(0, 10), p.value]))
  const lastMs = Math.max(...points.map((p) => Date.parse(p.date.slice(0, 10) + 'T00:00:00Z')))
  const startMs = lastMs - (days - 1) * DAY
  const vals = Array.from(byDate.entries())
    .filter(([d]) => Date.parse(d + 'T00:00:00Z') >= startMs)
    .map(([, v]) => v)
  const min = vals.length ? Math.min(...vals) : 0
  const max = vals.length ? Math.max(...vals) : 0
  const bin = (v: number) => {
    if (max === min) return 2
    return Math.min(4, Math.max(1, Math.floor(((v - min) / (max - min)) * 4) + 1))
  }
  const cells = Array.from({ length: days }, (_, i) => {
    const ms = startMs + i * DAY
    const date = iso(ms)
    const v = byDate.get(date)
    // lunes = 0 ... domingo = 6
    const weekday = (new Date(ms).getUTCDay() + 6) % 7
    return { date, v, weekday, ms }
  })
  const firstWeekday = cells[0].weekday
  return (
    <figure className="heat">
      <div className="heat-grid" role="img"
           aria-label={`${label}: ${days} días hasta ${iso(lastMs)}, de ${fmt(min)} a ${fmt(max)} ${unit}`}>
        {cells.map((c, i) => {
          const col = Math.floor((i + firstWeekday) / 7) + 1
          const style = { gridColumn: col, gridRow: c.weekday + 1 }
          return c.v === undefined ? (
            <span key={c.date} className="heat-cell gap" style={style} title={`${c.date}: sin dato`} />
          ) : (
            <span key={c.date} className={`heat-cell heat-${bin(c.v)}`} style={style}
                  title={`${c.date}: ${fmt(c.v)} ${unit}`} />
          )
        })}
      </div>
      <p className="heat-legend">
        <span>{fmt(min)} {unit}</span>
        {[1, 2, 3, 4].map((n) => (
          <span key={n} className={`heat-cell heat-${n}`} aria-hidden="true" />
        ))}
        <span>{fmt(max)} {unit}</span>
        <span className="heat-cell gap" aria-hidden="true" /> <span>sin dato</span>
      </p>
      <DataTable
        caption={`${label} (${unit})`}
        headers={['Fecha', `Valor (${unit})`]}
        rows={cells.map((c) => [c.date, c.v === undefined ? 'sin dato' : fmt(c.v)])}
      />
    </figure>
  )
}
