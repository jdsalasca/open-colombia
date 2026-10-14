import type { Point, Series } from './types'

const nf = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 })

export const fmt = (v: number): string => nf.format(v)

export function latest(s: Series): Point | null {
  if (s.points.length === 0) return null
  return s.points.reduce((a, b) => (a.date >= b.date ? a : b))
}

/** Penúltimo punto por fecha; null si hay menos de dos. */
export function previous(s: Series): Point | null {
  if (s.points.length < 2) return null
  const sorted = [...s.points].sort((a, b) => a.date.localeCompare(b.date))
  return sorted[sorted.length - 2]
}

/** Variación del último dato frente al anterior; null si no hay dos puntos. */
export function delta(s: Series): number | null {
  const prev = previous(s)
  const last = latest(s)
  if (!prev || !last) return null
  return last.value - prev.value
}

export function periodLabel(date: string, periodicity: string): string {
  const [y, m] = date.split('-')
  if (periodicity === 'mensual') {
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
    return `${meses[Number(m) - 1] ?? m} ${y}`
  }
  return y
}

export interface Ols {
  slope: number // unidades por año
  intercept: number
  r2: number
}

const YEAR_MS = 365.25 * 24 * 3600 * 1000

/** MCO del valor contra el tiempo (años desde el primer dato). Con menos de 5 puntos no concluye. */
export function ols(points: Point[]): Ols | null {
  if (points.length < 5) return null
  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  const t0 = Date.parse(sorted[0].date)
  const xs = sorted.map((p) => (Date.parse(p.date) - t0) / YEAR_MS)
  const ys = sorted.map((p) => p.value)
  const n = xs.length
  const mx = xs.reduce((a, b) => a + b, 0) / n
  const my = ys.reduce((a, b) => a + b, 0) / n
  let sxx = 0
  let sxy = 0
  let syy = 0
  for (let i = 0; i < n; i++) {
    sxx += (xs[i] - mx) ** 2
    sxy += (xs[i] - mx) * (ys[i] - my)
    syy += (ys[i] - my) ** 2
  }
  const slope = sxx === 0 ? 0 : sxy / sxx
  const intercept = my - slope * mx
  const r2 = sxx === 0 || syy === 0 ? 0 : (sxy * sxy) / (sxx * syy)
  return { slope, intercept, r2 }
}
