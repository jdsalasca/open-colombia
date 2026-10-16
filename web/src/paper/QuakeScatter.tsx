import { DataTable } from '../components/DataTable'
import { bogotaDate, fmt } from '../format'
import type { Quake } from '../types'

/** Magnitud contra tiempo; el radio crece con la magnitud y el mayor evento va rotulado. */
export function QuakeScatter({ quakes }: { quakes: Quake[] }) {
  if (quakes.length === 0) return <p className="card-empty">sin sismos en el periodo</p>
  const width = 520
  const height = 170
  const padL = 34
  const padR = 12
  const padT = 22
  const padB = 26
  const times = quakes.map((q) => Date.parse(q.time))
  const t0 = Math.min(...times)
  const tN = Math.max(...times)
  const tSpan = tN - t0
  const mags = quakes.map((q) => q.mag)
  const mMin = Math.floor(Math.min(...mags))
  const mMax = Math.ceil(Math.max(...mags)) || mMin + 1
  const mSpan = mMax - mMin || 1
  const x = (ms: number) => (tSpan === 0 ? (padL + width - padR) / 2 : padL + ((ms - t0) / tSpan) * (width - padL - padR))
  const y = (m: number) => height - padB - ((m - mMin) / mSpan) * (height - padT - padB)
  const biggest = quakes.reduce((a, b) => (b.mag > a.mag ? b : a))
  const labelX = Math.min(Math.max(x(Date.parse(biggest.time)), padL + 60), width - padR - 60)
  return (
    <figure className="scatter">
      <svg viewBox={`0 0 ${width} ${height}`} role="img"
           aria-label={`${quakes.length} sismos entre ${bogotaDate(new Date(t0).toISOString())} y ${bogotaDate(new Date(tN).toISOString())}; el mayor fue M ${fmt(biggest.mag)}`}>
        <line x1={padL} x2={width - padR} y1={height - padB} y2={height - padB} className="axis" />
        <text x={padL - 6} y={y(mMax) + 4} textAnchor="end" className="tick">M {mMax}</text>
        <text x={padL - 6} y={y(mMin) + 4} textAnchor="end" className="tick">M {mMin}</text>
        <text x={padL} y={height - 8} className="tick">{bogotaDate(new Date(t0).toISOString())}</text>
        <text x={width - padR} y={height - 8} textAnchor="end" className="tick">{bogotaDate(new Date(tN).toISOString())}</text>
        {quakes.map((q) => (
          <circle key={q.time + q.lat + q.lon} className="quake" cx={x(Date.parse(q.time))} cy={y(q.mag)}
                  r={2.5 + (q.mag - mMin) * 1.6}>
            <title>{`M ${fmt(q.mag)} · ${q.place} · ${bogotaDate(q.time)}`}</title>
          </circle>
        ))}
        <text className="mark-text" x={labelX} y={Math.max(12, y(biggest.mag) - 10)} textAnchor="middle">
          {`M ${fmt(biggest.mag)} · ${biggest.place}`}
        </text>
      </svg>
      <DataTable
        caption="Sismos del catálogo USGS"
        headers={['Fecha', 'Magnitud', 'Lugar', 'Profundidad (km)']}
        rows={quakes.map((q) => [bogotaDate(q.time), fmt(q.mag), q.place, fmt(q.depthKm)])}
      />
    </figure>
  )
}
