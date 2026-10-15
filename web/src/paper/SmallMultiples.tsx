import { fmt } from '../format'
import type { Point } from '../types'

interface Item {
  label: string
  points: Point[]
}

/** Una mini-línea por serie con la misma escala de tiempo, cada una con su propia escala vertical. */
export function SmallMultiples({ series }: { series: Item[] }) {
  const all = series.flatMap((s) => s.points.map((p) => Date.parse(p.date)))
  const t0 = all.length ? Math.min(...all) : 0
  const tN = all.length ? Math.max(...all) : 1
  const tSpan = tN - t0 || 1
  const w = 220
  const h = 64
  const pad = 6
  return (
    <div className="multiples">
      {series.map((s) => {
        const pts = [...s.points].sort((a, b) => a.date.localeCompare(b.date))
        if (pts.length === 0) {
          return (
            <figure key={s.label} className="multiple">
              <figcaption>{s.label}</figcaption>
              <p className="card-empty">sin datos</p>
            </figure>
          )
        }
        const vals = pts.map((p) => p.value)
        const min = Math.min(...vals)
        const max = Math.max(...vals)
        const span = max - min || 1
        const x = (d: string) => pad + ((Date.parse(d) - t0) / tSpan) * (w - pad * 2 - 34)
        const y = (v: number) => h - pad - ((v - min) / span) * (h - pad * 2)
        const d = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.date).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
        const last = pts[pts.length - 1]
        return (
          <figure key={s.label} className="multiple">
            <figcaption>{s.label}</figcaption>
            <svg className="mini" viewBox={`0 0 ${w} ${h}`} role="img"
                 aria-label={`${s.label}: ${pts.length} datos, último ${fmt(last.value)}, de ${fmt(min)} a ${fmt(max)}`}>
              <path d={d} className="line" />
              <circle cx={x(last.date)} cy={y(last.value)} r={3.5} className="dot-end" />
              <text className="end-label" x={Math.min(x(last.date) + 7, w - 2)} y={y(last.value) + 4}>{fmt(last.value)}</text>
            </svg>
          </figure>
        )
      })}
    </div>
  )
}
