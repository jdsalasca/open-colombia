import { DataTable } from '../components/DataTable'
import { fmt } from '../format'
import { patternFill } from './ChartDefs'
import { LegendTags } from './LegendTags'

interface Props {
  categories: string[]
  periods: string[]
  /** values[periodo][categoría]; null = sin dato (no se dibuja y la tabla lo dice). */
  values: Array<Array<number | null>>
  caption?: string
  unit?: string
}

/** Barras apiladas por periodo. Cada categoría lleva color Y textura (patrón SVG). */
export function StackedBars({ categories, periods, values, caption = 'Composición por periodo', unit = '' }: Props) {
  const total = (row: Array<number | null>) => row.reduce<number>((a, v) => a + (v ?? 0), 0)
  if (periods.length === 0 || values.every((row) => total(row) === 0)) {
    return <p className="card-empty">sin datos para este periodo</p>
  }
  const width = 640
  const height = 200
  const padL = 46
  const padR = 10
  const padT = 10
  const padB = 28
  const max = Math.max(...values.map(total)) || 1
  const slot = (width - padL - padR) / periods.length
  const barW = Math.max(6, slot * 0.7)
  const y = (v: number) => height - padB - (v / max) * (height - padT - padB)

  return (
    <figure className="stacked">
      <svg viewBox={`0 0 ${width} ${height}`} role="img"
           aria-label={`${caption}: ${periods.length} periodos, de ${periods[0]} a ${periods[periods.length - 1]}`}>
        <line x1={padL} x2={width - padR} y1={y(0)} y2={y(0)} className="axis" />
        <text x={padL - 6} y={y(max) + 4} textAnchor="end" className="tick">{fmt(max)}</text>
        <text x={padL - 6} y={y(0) + 4} textAnchor="end" className="tick">0</text>
        {values.map((row, p) => {
          let acc = 0
          return row.map((v, c) => {
            if (!v || v <= 0) return null
            const top = y(acc + v)
            const h = y(acc) - top
            acc += v
            return (
              <rect key={`${p}-${c}`} className="stack" fill={patternFill(c)}
                    x={padL + p * slot + (slot - barW) / 2} y={top} width={barW} height={Math.max(0.5, h)}>
                <title>{`${periods[p]} · ${categories[c]}: ${fmt(v)}`}</title>
              </rect>
            )
          })
        })}
        <text x={padL} y={height - 8} className="tick">{periods[0]}</text>
        <text x={width - padR} y={height - 8} textAnchor="end" className="tick">{periods[periods.length - 1]}</text>
      </svg>
      <LegendTags items={categories.map((label, index) => ({ label, index }))} />
      <DataTable
        caption={caption}
        headers={['Periodo', ...categories.map((c) => (unit ? `${c} (${unit})` : c))]}
        rows={periods.map((p, i) => [p, ...values[i].map((v) => (v === null ? 'sin dato' : fmt(v)))])}
      />
    </figure>
  )
}
