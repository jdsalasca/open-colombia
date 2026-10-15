import { DataTable } from '../components/DataTable'
import { fmt } from '../format'
import type { BreakdownItem } from '../types'

interface Props {
  items: BreakdownItem[]
  caption?: string
}

/** Barras horizontales ordenadas de mayor a menor; para muchas categorías, donde una torta no se lee. */
export function HBarChart({ items, caption = 'Composición' }: Props) {
  const OTHERS = 'OTROS'
  // de mayor a menor, pero el cajón «OTROS» siempre al final aunque sea el más grande
  const sorted = [...items]
    .filter((i) => Number.isFinite(i.value))
    .sort((a, b) =>
      a.label === OTHERS ? 1 : b.label === OTHERS ? -1 : b.value - a.value)
  if (sorted.length === 0) return <p className="card-empty">sin datos en este rango</p>
  const max = Math.max(...sorted.map((i) => i.value)) || 1
  return (
    <figure className="hbars">
      <ul>
        {sorted.map((i, idx) => (
          <li key={i.label}>
            <span className="hbar-label">{i.label}</span>
            <span className="hbar-track">
              <span className={`hbar-fill hpat-${idx % 7}`} style={{ width: `${Math.max(2, (i.value / max) * 100)}%` }} />
            </span>
            <span className="hbar-value">{fmt(i.value)} ({fmt(i.share * 100)} %)</span>
          </li>
        ))}
      </ul>
      <DataTable
        caption={caption}
        headers={['Categoría', 'Valor', '%']}
        rows={sorted.map((i) => [i.label, fmt(i.value), fmt(i.share * 100)])}
      />
    </figure>
  )
}
