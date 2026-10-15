import { DataTable } from '../components/DataTable'
import { fmt } from '../format'

export interface Bin {
  from: number
  to: number
  count: number
}

/** Cubetas contiguas de ancho fijo desde el piso del mínimo; las vacías del medio se conservan. */
export function histogramBins(values: number[], width: number): Bin[] {
  const finite = values.filter((v) => Number.isFinite(v))
  if (finite.length === 0 || width <= 0) return []
  const start = Math.floor(Math.min(...finite) / width + 1e-9) * width
  const n = Math.floor((Math.max(...finite) - start) / width + 1e-9) + 1
  const bins: Bin[] = Array.from({ length: n }, (_, i) => ({
    from: +(start + i * width).toFixed(6),
    to: +(start + (i + 1) * width).toFixed(6),
    count: 0,
  }))
  for (const v of finite) {
    const idx = Math.min(n - 1, Math.max(0, Math.floor((v - start) / width + 1e-9)))
    bins[idx].count++
  }
  return bins
}

interface Props {
  values: number[]
  binWidth: number
  unit: string
  caption?: string
}

export function Histogram({ values, binWidth, unit, caption = 'Distribución' }: Props) {
  const bins = histogramBins(values, binWidth)
  if (bins.length === 0) return <p className="card-empty">sin sismos en el periodo</p>
  const width = 520
  const height = 150
  const padL = 30
  const padB = 28
  const padT = 14
  const max = Math.max(...bins.map((b) => b.count)) || 1
  const slot = (width - padL - 8) / bins.length
  const barW = Math.max(6, slot * 0.78)
  return (
    <figure className="hist">
      <svg viewBox={`0 0 ${width} ${height}`} role="img"
           aria-label={`${caption}: ${values.length} eventos en ${bins.length} intervalos de ${fmt(binWidth)} ${unit}`}>
        <line x1={padL} x2={width - 8} y1={height - padB} y2={height - padB} className="axis" />
        <text x={padL - 6} y={padT + 4} textAnchor="end" className="tick">{max}</text>
        <text x={padL - 6} y={height - padB + 4} textAnchor="end" className="tick">0</text>
        {bins.map((b, i) => {
          const h = (b.count / max) * (height - padB - padT)
          return (
            <g key={b.from}>
              <rect className="hist-bar" fill="url(#pat-0)" x={padL + i * slot + (slot - barW) / 2}
                    y={height - padB - h} width={barW} height={Math.max(b.count > 0 ? 1 : 0, h)}>
                <title>{`${fmt(b.from)}–${fmt(b.to)} ${unit}: ${b.count}`}</title>
              </rect>
              <text x={padL + i * slot + slot / 2} y={height - 10} textAnchor="middle" className="tick">{fmt(b.from)}</text>
            </g>
          )
        })}
      </svg>
      <DataTable
        caption={caption}
        headers={[`Intervalo (${unit})`, 'Eventos']}
        rows={bins.map((b) => [`${fmt(b.from)} a ${fmt(b.to)}`, b.count])}
      />
    </figure>
  )
}
