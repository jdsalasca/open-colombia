import type { Point } from '../types'

interface Props {
  points: Point[]
  width?: number
  height?: number
  stroke?: string
}

/** Mini gráfica de línea. Con menos de 2 puntos no dibuja trazo. */
export function Sparkline({ points, width = 140, height = 40, stroke = 'var(--ink)' }: Props) {
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  if (sorted.length < 2) {
    return <svg role="img" aria-label="sin serie para graficar" width={width} height={height} />
  }
  const vals = sorted.map((p) => p.value)
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const span = max - min || 1
  const pad = 3
  const d = sorted
    .map((p, i) => {
      const x = pad + (i / (sorted.length - 1)) * (width - pad * 2)
      const y = height - pad - ((p.value - min) / span) * (height - pad * 2)
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
  return (
    <svg role="img" aria-label={`tendencia de ${sorted.length} datos`} width={width} height={height}
         viewBox={`0 0 ${width} ${height}`}>
      <path d={d} fill="none" stroke={stroke} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
