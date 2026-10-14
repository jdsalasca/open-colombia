interface Props {
  ranges: string[]
  value: string
  onChange: (range: string) => void
}

const LABEL: Record<string, string> = { todo: 'todo' }

/** Chips de rango. Solo muestra los que la serie admite. */
export function RangeSelector({ ranges, value, onChange }: Props) {
  return (
    <div className="ranges" role="group" aria-label="Rango de tiempo">
      {ranges.map((r) => (
        <button
          key={r}
          type="button"
          className="range-chip"
          aria-pressed={r === value}
          onClick={() => onChange(r)}
        >
          {LABEL[r] ?? r}
        </button>
      ))}
    </div>
  )
}

/** Rangos válidos por periodicidad (espejo de Range.availableFor en el backend). */
export function rangesFor(periodicity: string): string[] {
  return periodicity === 'mensual' ? ['3m', '6m', '1a', '2a', 'todo'] : ['5a', '10a', 'todo']
}

/** Mensuales abren en 1 año; anuales en 10 años (una historia de 60 años aplasta la tendencia reciente). */
export function defaultRange(periodicity: string): string {
  return periodicity === 'mensual' ? '1a' : '10a'
}
