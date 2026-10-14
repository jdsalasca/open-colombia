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
  fetchedAt: string
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
