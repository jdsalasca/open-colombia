/**
 * Paleta de Open Colombia. Cada color de gráfico tiene al menos 4,5:1 de contraste sobre la hoja `paper`
 * (WCAG 2.x AA). El amarillo bandera solo se usa como fondo con texto `ink`. Si cambias un valor aquí,
 * cámbialo también en `paper.css`: `palette.test.ts` verifica que coincidan.
 */
export const PALETTE = {
  paper: '#f7f0e1',
  board: '#e9dcc3',
  kraft: '#d8bf94',
  ink: '#2b2118',
  inkSoft: '#6b5a48',
  yellow: '#fcd116',
  categorical: ['#0b4f8a', '#a84300', '#006b4f', '#9c3b78', '#7a5c00', '#00667a', '#59514a'],
  good: '#1b6b3a',
  bad: '#b3261e',
  neutral: '#0b4f8a',
} as const

function channel(c: number): number {
  const s = c / 255
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
}

function luminance(hex: string): number {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/** Razón de contraste WCAG 2.x entre dos colores `#rrggbb` (1 a 21). */
export function contrast(a: string, b: string): number {
  const la = luminance(a)
  const lb = luminance(b)
  const [hi, lo] = la >= lb ? [la, lb] : [lb, la]
  return (hi + 0.05) / (lo + 0.05)
}
