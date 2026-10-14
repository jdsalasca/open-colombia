import { DataTable } from '../components/DataTable'
import { fmt } from '../format'
import type { BreakdownItem } from '../types'

const COLORS = ['#c9342b', '#fcd116', '#1f4e9c', '#3f7d5c', '#d9653b', '#8a6bbe', '#6b5a48']

interface Props {
  items: BreakdownItem[]
  caption?: string
}

/** Torta. Una sola categoría se dibuja como círculo completo (un arco de 360° no se puede trazar). */
export function PieChart({ items, caption = 'Composición' }: Props) {
  const valid = items.filter((i) => i.share > 0 && Number.isFinite(i.share))
  if (valid.length === 0) return <p className="card-empty">sin datos en este rango</p>

  const cx = 100
  const cy = 100
  const r = 90
  let acc = 0
  const slices = valid.map((it, idx) => {
    const start = acc
    acc += it.share
    return { it, start, end: acc, color: COLORS[idx % COLORS.length] }
  })
  const point = (t: number) => {
    const a = 2 * Math.PI * t - Math.PI / 2
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)]
  }

  return (
    <figure className="pie">
      <svg viewBox="0 0 200 200" role="img" aria-label={`${caption}: ${valid.map((s) => `${s.label} ${fmt(s.share * 100)} %`).join(', ')}`}>
        {valid.length === 1 ? (
          <circle className="pie-full" cx={cx} cy={cy} r={r} fill={COLORS[0]} />
        ) : (
          slices.map((s) => {
            const [x1, y1] = point(s.start)
            const [x2, y2] = point(s.end)
            const large = s.end - s.start > 0.5 ? 1 : 0
            const d = `M${cx},${cy} L${x1.toFixed(2)},${y1.toFixed(2)} A${r},${r} 0 ${large} 1 ${x2.toFixed(2)},${y2.toFixed(2)} Z`
            return <path key={s.it.label} className="slice" d={d} fill={s.color} />
          })
        )}
      </svg>
      <ul className="legend">
        {slices.map((s) => (
          <li key={s.it.label}>
            <span className="swatch" style={{ background: s.color }} aria-hidden="true" />
            {s.it.label}: {fmt(s.it.share * 100)} % ({fmt(s.it.value)})
          </li>
        ))}
      </ul>
      <DataTable
        caption={caption}
        headers={['Categoría', 'Valor', '%']}
        rows={valid.map((i) => [i.label, fmt(i.value), fmt(i.share * 100)])}
      />
    </figure>
  )
}
