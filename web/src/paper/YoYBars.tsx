import { DataTable } from '../components/DataTable'
import { fmt } from '../format'
import type { Point } from '../types'

interface Props {
  points: Point[]
  /** true/false: subir es bueno/malo; null: neutro. */
  higherIsBetter: boolean | null
  maxBars?: number
}

interface Bar {
  year: string
  pct: number
}

/** Barras de variación interanual (%). Los saltos desde una base 0 se omiten. */
export function YoYBars({ points, higherIsBetter, maxBars = 20 }: Props) {
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  const bars: Bar[] = []
  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1].value
    if (prev === 0) continue
    const pct = ((sorted[i].value - prev) / Math.abs(prev)) * 100
    if (Number.isFinite(pct)) bars.push({ year: sorted[i].date.slice(0, 4), pct })
  }
  const shown = bars.slice(-maxBars)
  if (shown.length === 0) return <p className="card-empty">sin datos suficientes para variación interanual</p>

  const width = 640
  const height = 200
  const padX = 12
  const padY = 22
  const maxAbs = Math.max(...shown.map((b) => Math.abs(b.pct))) || 1
  const mid = height / 2
  const slot = (width - padX * 2) / shown.length
  const barW = Math.max(4, slot * 0.7)
  const cls = (pct: number) => {
    if (higherIsBetter === null) return 'bar-neutral'
    return (pct >= 0) === higherIsBetter ? 'bar-good' : 'bar-bad'
  }

  return (
    <figure className="yoy">
      <svg viewBox={`0 0 ${width} ${height}`} role="img"
           aria-label={`Variación interanual de ${shown[0].year} a ${shown[shown.length - 1].year}`}>
        <line x1={padX} x2={width - padX} y1={mid} y2={mid} className="axis" />
        {shown.map((b, i) => {
          const h = (Math.abs(b.pct) / maxAbs) * (mid - padY)
          const x = padX + i * slot + (slot - barW) / 2
          const y = b.pct >= 0 ? mid - h : mid
          return <rect key={b.year} className={`bar ${cls(b.pct)}`} x={x} y={y} width={barW} height={Math.max(1, h)} />
        })}
        <text x={padX} y={height - 6} className="tick">{shown[0].year}</text>
        <text x={width - padX} y={height - 6} textAnchor="end" className="tick">{shown[shown.length - 1].year}</text>
      </svg>
      <p className="chip">
        {higherIsBetter === null
          ? 'Color neutro: subir no es bueno ni malo por sí mismo.'
          : higherIsBetter
            ? 'Verde: sube (mejor). Rojo: baja (peor).'
            : 'Verde: baja (mejor). Rojo: sube (peor).'}
      </p>
      <DataTable
        caption="Variación interanual (%)"
        headers={['Año', 'Variación %']}
        rows={shown.map((b) => [b.year, fmt(b.pct)])}
      />
    </figure>
  )
}
