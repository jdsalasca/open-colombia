/**
 * Patrones SVG compartidos por todas las gráficas. Cada categoría tiene color Y textura, para que el
 * significado no dependa solo del color. Se monta una sola vez (en App) y se referencia con url(#pat-n).
 */
const COUNT = 7

/** Relleno de la categoría `i` (cíclico). */
export function patternFill(i: number): string {
  return `url(#pat-${((i % COUNT) + COUNT) % COUNT})`
}

const OVERLAY = 'var(--paper)'

/** Dibujo (encima del color base) de cada textura; la 0 es sólida. */
function texture(i: number) {
  const common = { stroke: OVERLAY, strokeWidth: 1.6, opacity: 0.7, fill: 'none' } as const
  switch (i % COUNT) {
    case 1:
      return <path d="M-1,9 l10,-10 M-1,3 l4,-4 M5,11 l4,-4" {...common} />
    case 2:
      return <circle cx="4.5" cy="4.5" r="1.5" fill={OVERLAY} opacity="0.75" />
    case 3:
      return <path d="M0,0.5 H9 M0.5,0 V9" {...common} />
    case 4:
      return <path d="M-1,-1 l10,10 M-1,5 l4,4 M5,-1 l4,4" {...common} />
    case 5:
      return <path d="M0,0 L9,9 M9,0 L0,9" {...common} />
    case 6:
      return <path d="M0,2.5 H9 M0,6.5 H9" {...common} />
    default:
      return null
  }
}

function Pat({ id, color, i }: { id: string; color: string; i: number }) {
  return (
    <pattern id={id} width="9" height="9" patternUnits="userSpaceOnUse">
      <rect width="9" height="9" style={{ fill: color }} />
      {texture(i)}
    </pattern>
  )
}

export function ChartDefs() {
  return (
    <svg width="0" height="0" aria-hidden="true" focusable="false" style={{ position: 'absolute' }}>
      <defs>
        {Array.from({ length: COUNT }, (_, i) => (
          <Pat key={i} id={`pat-${i}`} color={`var(--cat-${i + 1})`} i={i} />
        ))}
        <Pat id="pat-good" color="var(--good)" i={1} />
        <Pat id="pat-bad" color="var(--bad)" i={2} />
        <Pat id="pat-neutral" color="var(--neutral)" i={0} />
      </defs>
    </svg>
  )
}
