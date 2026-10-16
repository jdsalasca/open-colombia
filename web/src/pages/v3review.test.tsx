import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { periodLabel } from '../format'
import { LineChart } from '../paper/LineChart'
import { PaperCard } from '../paper/PaperCard'
import { QuakeScatter } from '../paper/QuakeScatter'
import { RatesPanel } from '../components/RatesPanel'
import type { IndicatorsResponse, LiveSnapshot, Series } from '../types'
import { ThemePage } from './ThemePage'
import { Today } from './Today'

afterEach(() => vi.unstubAllGlobals())

const dailyPoints = (n: number, last = '2026-10-13') =>
  Array.from({ length: n }, (_, i) => ({
    date: new Date(Date.parse(last + 'T00:00:00Z') - (n - 1 - i) * 86400000).toISOString().slice(0, 10),
    value: 20 + (i % 4),
  }))

const daily: Series = {
  id: 'temperatura-nacional', theme: 'ambiente-energia', name: 'Temperatura', unit: '°C', source: 'IDEAM',
  sourceUrl: 'u', periodicity: 'diaria', fetchedAt: null, points: dailyPoints(40),
}

describe('daily dates show the day, not just the year', () => {
  it('periodLabel for a daily series is "d mmm yyyy"', () => {
    expect(periodLabel('2026-10-13', 'diaria')).toBe('13 oct 2026')
    expect(periodLabel('2026-01-05', 'diaria')).toBe('5 ene 2026')
  })

  it('the card compares with the previous DAY and the chip says which day', () => {
    render(<MemoryRouter><PaperCard series={daily} /></MemoryRouter>)
    expect(screen.getByText(/vs\.\s+12 oct 2026/)).toBeInTheDocument()
    expect(screen.getByText(/13 oct 2026/, { selector: '.chip a + *, .chip' })).toBeInTheDocument()
  })

  it('the rates window names the first and last day', () => {
    render(
      <RatesPanel unit="°C" periodicity="diaria" rates={{
        n: 8, from: '2026-10-06', to: '2026-10-13', change: 1, changePct: 5, annualizedPct: null, mean: 21,
        min: { date: '2026-10-07', value: 20 }, max: { date: '2026-10-12', value: 23 }, stdDev: 1 }} />,
    )
    expect(screen.getByText(/Ventana: del 6 oct 2026 al 13 oct 2026/)).toBeInTheDocument()
    expect(screen.getByText('Mínimo').nextElementSibling).toHaveTextContent('7 oct 2026')
  })

  it('a 7-day daily window labels the x axis with days, not "oct 2026 … oct 2026"', () => {
    const { container } = render(<LineChart points={dailyPoints(8)} unit="°C" />)
    const ticks = Array.from(container.querySelectorAll('text.tick')).map((t) => t.textContent ?? '')
    expect(ticks.some((t) => /6 oct 2026/.test(t))).toBe(true)
    expect(ticks.some((t) => /13 oct 2026/.test(t))).toBe(true)
  })
})

describe('no per-year trend for daily windows', () => {
  const data: IndicatorsResponse = { themes: [{ id: 'ambiente-energia', name: 'Ambiente', series: [daily] }], updatedAt: null }

  it('a daily series draws no "por año" trend line (seasonal noise is not a yearly trend)', async () => {
    const body = {
      series: daily, range: '90d', availableRanges: ['7d', '30d', '90d', '1a', 'todo'], higherIsBetter: null, rates: null,
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 200 })))
    const { container } = render(
      <MemoryRouter initialEntries={['/tema/ambiente-energia']}>
        <Routes><Route path="/tema/:id" element={<ThemePage data={data} insights={[]} live={null} />} /></Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(container.querySelector('svg.linechart')).not.toBeNull())
    expect(container.querySelector('line.trend')).toBeNull()
    expect(screen.queryByText(/por año/)).toBeNull()
  })
})

describe('quake copy does not promise magnitudes the USGS catalog does not have', () => {
  const live: LiveSnapshot = {
    usdCop: null, fetchedAt: '2026-10-14T18:05:00Z', errors: {},
    quakes: [{ time: '2026-10-13T21:24:00Z', mag: 4.8, place: 'Cepitá, Colombia', lat: 6.7, lon: -73, depthKm: 162 }],
  }

  it('Today says "catálogo USGS" and never claims M ≥ 2,5', () => {
    const { container } = render(
      <MemoryRouter><Today live={live} data={{ themes: [], updatedAt: null }} now={new Date('2026-10-14T18:10:00Z')} /></MemoryRouter>,
    )
    expect(container.textContent).not.toMatch(/2,5/)
    expect(container.textContent).toMatch(/catálogo USGS/i)
  })

  it('the quake chart explains the catalog covers M 4 and above and that the SGC records more', () => {
    const data: IndicatorsResponse = { themes: [{ id: 'ambiente-energia', name: 'A', series: [] }], updatedAt: null }
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    const { container } = render(
      <MemoryRouter initialEntries={['/tema/ambiente-energia']}>
        <Routes><Route path="/tema/:id" element={<ThemePage data={data} insights={[]} live={live} />} /></Routes>
      </MemoryRouter>,
    )
    expect(container.textContent).not.toMatch(/2,5/)
    expect(container.textContent).toMatch(/Servicio Geológico Colombiano/)
  })

  it('the empty scatter does not claim a magnitude threshold either', () => {
    render(<QuakeScatter quakes={[]} />)
    expect(screen.queryByText(/2,5/)).toBeNull()
    expect(screen.getByText(/sin sismos/i)).toBeInTheDocument()
  })
})
