import type { IndicatorsResponse, Insight, RefreshOutcome, RefreshReport } from './types'

const BASE: string = import.meta.env.VITE_API_BASE ?? ''

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) throw new Error(`HTTP ${res.status} en ${path}`)
  return (await res.json()) as T
}

export const getIndicators = (): Promise<IndicatorsResponse> => getJson('/api/indicators')

export const getInsights = (): Promise<Insight[]> => getJson('/api/insights')

export async function refresh(): Promise<RefreshOutcome> {
  const res = await fetch(`${BASE}/api/refresh`, { method: 'POST' })
  if (res.status === 429) {
    const header = Number(res.headers.get('Retry-After'))
    return { rateLimited: true, retryAfter: Number.isFinite(header) && header > 0 ? header : 60 }
  }
  if (!res.ok) throw new Error(`HTTP ${res.status} al actualizar`)
  return { rateLimited: false, report: (await res.json()) as RefreshReport }
}
