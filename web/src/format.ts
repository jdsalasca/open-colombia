import type { Series } from './types'

const nf = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 })

export const fmt = (v: number): string => nf.format(v)

export function latest(s: Series) {
  if (s.points.length === 0) return null
  return s.points.reduce((a, b) => (a.date >= b.date ? a : b))
}

/** Variación del último dato frente al anterior; null si no hay dos puntos. */
export function delta(s: Series): number | null {
  const sorted = [...s.points].sort((a, b) => a.date.localeCompare(b.date))
  if (sorted.length < 2) return null
  return sorted[sorted.length - 1].value - sorted[sorted.length - 2].value
}

export function periodLabel(date: string, periodicity: string): string {
  const [y, m] = date.split('-')
  if (periodicity === 'mensual') {
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
    return `${meses[Number(m) - 1] ?? m} ${y}`
  }
  return y
}
