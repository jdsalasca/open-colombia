import { ApiError } from './types'
import type {
  Breakdown,
  IndicatorsResponse,
  Insight,
  LiveSnapshot,
  Methodology,
  RefreshOutcome,
  RefreshReport,
  SeriesResponse,
} from './types'

const BASE: string = import.meta.env.VITE_API_BASE ?? ''

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) {
    let ranges: string[] | undefined
    let message = `HTTP ${res.status} en ${path}`
    try {
      const body = (await res.json()) as { error?: string; availableRanges?: string[] }
      ranges = body.availableRanges
      if (body.error) message = body.error
    } catch {
      /* cuerpo no JSON */
    }
    throw new ApiError(message, res.status, ranges)
  }
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

export const getSeries = (id: string, range?: string): Promise<SeriesResponse> =>
  getJson(`/api/series/${encodeURIComponent(id)}${range ? `?range=${encodeURIComponent(range)}` : ''}`)

export const getMethodology = (id: string): Promise<Methodology> =>
  getJson(`/api/methodology/${encodeURIComponent(id)}`)

export const getMethodologyIndex = (): Promise<Methodology[]> => getJson('/api/methodology')

export const getBreakdown = (dim: string, range: string): Promise<Breakdown> =>
  getJson(`/api/breakdowns/homicidios?dim=${encodeURIComponent(dim)}&range=${encodeURIComponent(range)}`)

export const getLive = (): Promise<LiveSnapshot> => getJson('/api/live')
