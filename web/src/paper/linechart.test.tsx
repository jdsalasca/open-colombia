import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Breakdowns } from '../pages/Breakdowns'
import { MethodologyIndex } from '../pages/MethodologyIndex'
import { ThemePage } from '../pages/ThemePage'
import type { IndicatorsResponse, Point, Series } from '../types'
import { LineChart } from './LineChart'

afterEach(() => vi.unstubAllGlobals())

const yearly = (vals: number[]): Point[] => vals.map((value, i) => ({ date: `${2010 + i}-01-01`, value }))
const monthly = (vals: number[]): Point[] =>
  vals.map((value, i) => ({ date: `2026-${String(i + 1).padStart(2, '0')}-01`, value }))

describe('LineChart', () => {
  it('is compact by default (viewBox height 170)', () => {
    const { container } = render(<LineChart points={yearly([1, 2, 3, 4])} unit="%" />)
    expect(container.querySelector('svg.linechart')?.getAttribute('viewBox')).toBe('0 0 640 170')
  })

  it('marks min and max with shapes and text, not with red/green only', () => {
    const { container } = render(<LineChart points={yearly([5, 1, 9, 4])} unit="%" />)
    expect(screen.getByText(/mín/)).toBeInTheDocument()
    expect(screen.getByText(/máx/)).toBeInTheDocument()
    expect(container.querySelector('path.mark-min')).not.toBeNull()
    expect(container.querySelector('path.mark-max')).not.toBeNull()
  })

  it('clips the trend line to the plot area instead of clamping each end', () => {
    const { container } = render(
      <LineChart points={yearly([0, 0, 0, 0, 10])} unit="%" trend={{ slope: 2, intercept: -2, r2: 0.6 }} />,
    )
    const line = container.querySelector('line.trend')!
    expect(line.getAttribute('clip-path')).toMatch(/^url\(#/)
    expect(container.querySelector('clipPath')).not.toBeNull()
  })

  it('shows month labels on short monthly series', () => {
    const { container } = render(<LineChart points={monthly([1, 2, 3, 4, 5, 6])} unit="COP" />)
    const ticks = Array.from(container.querySelectorAll('text.tick')).map((t) => t.textContent)
    expect(ticks.some((t) => /ene 2026/.test(t ?? ''))).toBe(true)
    expect(ticks.some((t) => /jun 2026/.test(t ?? ''))).toBe(true)
  })

  it('uses the custom xLabel formatter when given', () => {
    const { container } = render(
      <LineChart points={yearly([1, 2, 3])} unit="%" xLabel={(d) => `D:${d.slice(0, 4)}`} />,
    )
    expect(Array.from(container.querySelectorAll('text.tick')).some((t) => /D:2010/.test(t.textContent ?? ''))).toBe(true)
  })
})

const monthlySeries: Series = {
  id: 'trm', theme: 'economia', name: 'TRM', unit: 'COP/USD', source: 'datos.gov.co', sourceUrl: 'u',
  periodicity: 'mensual', fetchedAt: null,
  points: Array.from({ length: 24 }, (_, i) => ({
    date: `${2025 + Math.floor(i / 12)}-${String((i % 12) + 1).padStart(2, '0')}-01`, value: 3000 + i * 10,
  })),
}
const data: IndicatorsResponse = {
  themes: [{ id: 'economia', name: 'Economía', series: [monthlySeries] }], updatedAt: null,
}

describe('SeriesBlock loading state', () => {
  it('shows "Cargando" and hides the previous range rates while a new range loads', async () => {
    const body = {
      series: monthlySeries, range: '1a', availableRanges: ['3m', '6m', '1a', '2a', 'todo'], higherIsBetter: null,
      rates: { n: 13, from: '2025-10-01', to: '2026-10-01', change: 120, changePct: 3.9, annualizedPct: 3.9, mean: 3100,
        min: { date: '2025-10-01', value: 3000 }, max: { date: '2026-10-01', value: 3240 }, stdDev: 30 },
    }
    let release!: (r: Response) => void
    let first = true
    const fetchMock = vi.fn().mockImplementation(() => {
      if (first) {
        first = false
        return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }))
      }
      return new Promise<Response>((r) => (release = r))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(
      <MemoryRouter initialEntries={['/tema/economia']}>
        <Routes>
          <Route path="/tema/:id" element={<ThemePage data={data} insights={[]} />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(screen.getByText(/Variación/)).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: '6m' }))
    expect(await screen.findByText(/Cargando/)).toBeInTheDocument()
    expect(screen.queryByText('Variación')).toBeNull() // no mostrar tasas del rango anterior
    release(new Response(JSON.stringify({ ...body, range: '6m' }), { status: 200 }))
    await waitFor(() => expect(screen.getByText('Variación')).toBeInTheDocument())
  })
})

describe('chip groups are announced for what they are', () => {
  it('names the breakdown dimension and period groups', async () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    render(<Breakdowns />)
    expect(screen.getByRole('group', { name: 'Dimensión' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Periodo' })).toBeInTheDocument()
  })
})

describe('methodology glossary', () => {
  it('covers prediction interval, change correlation and the percentage-series rule', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]', { status: 200 })))
    render(
      <MemoryRouter>
        <MethodologyIndex />
      </MemoryRouter>,
    )
    expect(screen.getAllByText(/Intervalo de predicción/i).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Correlación de variaciones/i).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/ya son porcentajes/i).length).toBeGreaterThan(0)
  })
})
