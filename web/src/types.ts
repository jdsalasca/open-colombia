export interface Point {
  date: string // ISO yyyy-mm-dd
  value: number
}

export interface Series {
  id: string
  theme: string
  name: string
  unit: string
  source: string
  sourceUrl: string
  periodicity: string
  points: Point[]
  fetchedAt: string | null
}

export interface ThemeView {
  id: string
  name: string
  series: Series[]
}

export interface IndicatorsResponse {
  themes: ThemeView[]
  updatedAt: string | null
}

export interface Confidence {
  n: number
  r2: number | null
  level: 'alta' | 'media' | 'baja' | 'insuficiente'
}

export interface Insight {
  themeId: string
  title: string
  finding: string
  literature: string
  proposal: string
  confidence: Confidence
}

export interface SourceResult {
  id: string
  ok: boolean
  error: string | null
}

export interface RefreshReport {
  at: string
  results: SourceResult[]
}

export type RefreshOutcome =
  | { rateLimited: false; report: RefreshReport }
  | { rateLimited: true; retryAfter: number }

export interface RatePoint {
  date: string
  value: number
}

export interface Rates {
  n: number
  from: string
  to: string
  change: number
  changePct: number | null
  annualizedPct: number | null
  mean: number
  min: RatePoint
  max: RatePoint
  stdDev: number
}

export interface SeriesResponse {
  series: Series
  range: string
  availableRanges: string[]
  rates: Rates | null
  higherIsBetter: boolean | null
}

export interface Methodology {
  id: string
  name: string
  theme: string
  description: string
  method: string
  source: string
  sourceUrl: string
  queryUrl: string
  /** Solo fuentes consultadas por POST (XM): cuerpo JSON de la petición. */
  queryBody: string | null
  seriesUrl: string
  periodicity: string
  unit: string
  higherIsBetter: boolean | null
}

export interface BreakdownItem {
  label: string
  value: number
  share: number
}

export interface Breakdown {
  dim: string
  range: string
  from: string
  to: string
  total: number
  items: BreakdownItem[]
  source: string
  queryUrl: string
}

export class ApiError extends Error {
  status: number
  availableRanges?: string[]

  constructor(message: string, status: number, availableRanges?: string[]) {
    super(message)
    this.status = status
    this.availableRanges = availableRanges
  }
}
