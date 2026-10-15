import { patternFill } from './ChartDefs'

export interface LegendItem {
  label: string
  value?: string
  /** Índice de categoría: define color y textura, igual que en la gráfica. */
  index: number
}

/** Leyenda con aspecto de etiqueta de papel: muestra (color + textura), nombre y valor. */
export function LegendTags({ items }: { items: LegendItem[] }) {
  if (items.length === 0) return null
  return (
    <ul className="legend-tags">
      {items.map((it) => (
        <li key={`${it.index}-${it.label}`}>
          <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
            <rect width="16" height="16" rx="2" fill={patternFill(it.index)} />
          </svg>
          <span className="tag-label">{it.label}</span>
          {it.value !== undefined && <span className="tag-value">{it.value}</span>}
        </li>
      ))}
    </ul>
  )
}
