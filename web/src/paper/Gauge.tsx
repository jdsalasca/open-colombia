interface Props {
  value: number
  min: number
  max: number
  label: string
}

/** Medidor semicircular. Valores fuera de rango se acotan; rango degenerado no produce NaN. */
export function Gauge({ value, min, max, label }: Props) {
  const span = max - min
  const t = span > 0 ? Math.min(1, Math.max(0, (value - min) / span)) : 0
  const angle = Math.PI * (1 - t)
  const cx = 100
  const cy = 100
  const r = 80
  const x = cx + r * Math.cos(angle)
  const y = cy - r * Math.sin(angle)
  return (
    <svg viewBox="0 0 200 120" role="img" aria-label={`${label}: ${value}`} className="gauge">
      <path d={`M${cx - r},${cy} A${r},${r} 0 0 1 ${cx + r},${cy}`} className="gauge-bg" />
      <path d={`M${cx - r},${cy} A${r},${r} 0 0 1 ${x.toFixed(1)},${y.toFixed(1)}`} className="gauge-fg" />
    </svg>
  )
}
