import { fmt } from '../format'
import type { Point } from '../types'

interface Props {
  points: Point[]
  unit: string
  height?: number
}

/** Gráfica de línea con eje mínimo/máximo y puntos; sin datos muestra mensaje. */
export function LineChart({ points, unit, height = 220 }: Props) {
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
  const x = (i: number) => padL + (i / (sorted.length - 1)) * (width - padL - padR)
  const y = (v: number) => height - padB - ((v - min) / span) * (height - padT - padB)
  const d = sorted.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
  const first = sorted[0].date.slice(0, 4)
  const lastYear = sorted[sorted.length - 1].date.slice(0, 4)
  return (
    <svg className="linechart" viewBox={`0 0 ${width} ${height}`} role="img"
         aria-label={`Serie de ${sorted.length} datos entre ${first} y ${lastYear}, de ${fmt(min)} a ${fmt(max)} ${unit}`}>
      <line x1={padL} x2={width - padR} y1={y(min)} y2={y(min)} className="axis" />
      <line x1={padL} x2={width - padR} y1={y(max)} y2={y(max)} className="axis" />
      <text x={padL - 6} y={y(max) + 4} textAnchor="end" className="tick">{fmt(max)}</text>
      <text x={padL - 6} y={y(min) + 4} textAnchor="end" className="tick">{fmt(min)}</text>
      <text x={padL} y={height - 8} className="tick">{first}</text>
      <text x={width - padR} y={height - 8} textAnchor="end" className="tick">{lastYear}</text>
      <path d={d} className="line" />
      {sorted.length <= 40 &&
        sorted.map((p, i) => <circle key={p.date} cx={x(i)} cy={y(p.value)} r={3} className="dot" />)}
    </svg>
  )
}
