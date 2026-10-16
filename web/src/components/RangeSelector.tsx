interface Props {
  ranges: string[]
  value: string
  onChange: (range: string) => void
  /** Nombre accesible del grupo: «Rango de tiempo», «Dimensión», etc. */
  label?: string
}

const LABEL: Record<string, string> = { todo: 'todo' }

/** Chips de rango. Solo muestra los que la serie admite. */
export function RangeSelector({ ranges, value, onChange, label = 'Rango de tiempo' }: Props) {
  return (
    <div className="ranges" role="group" aria-label={label}>
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
  if (periodicity === 'diaria') return ['7d', '30d', '90d', '1a', 'todo']
  return periodicity === 'mensual' ? ['3m', '6m', '1a', '2a', 'todo'] : ['5a', '10a', 'todo']
}

/** Diarias abren en 90 días; mensuales en 1 año; anuales en 10 años (60 años de historia aplastan lo reciente). */
export function defaultRange(periodicity: string): string {
  if (periodicity === 'diaria') return '90d'
  return periodicity === 'mensual' ? '1a' : '10a'
}
