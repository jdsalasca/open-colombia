import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { IndicatorsResponse, LiveSnapshot, Series } from '../types'
import { Today } from './Today'

const NOW = new Date('2026-10-14T18:10:00Z')

const live: LiveSnapshot = {
  usdCop: {
    ticks: [
      { t: '2026-10-14T13:00:00Z', v: 3200 },
      { t: '2026-10-14T16:00:00Z', v: 3190 },
      { t: '2026-10-14T18:00:00Z', v: 3197.56 },
    ],
    last: 3197.56, open: 3200, changePct: -0.076, lastTickAt: '2026-10-14T18:00:00Z',
  },
  quakes: [
    { time: '2026-10-13T21:24:00Z', mag: 4.8, place: '5 km NW of Cepitá, Colombia', lat: 6.7, lon: -73, depthKm: 162 },
    { time: '2026-10-10T05:00:00Z', mag: 3.1, place: 'Chocó', lat: 5, lon: -76, depthKm: 20 },
    { time: '2026-09-01T05:00:00Z', mag: 5.0, place: 'Antiguo', lat: 4, lon: -74, depthKm: 10 },
  ],
  fetchedAt: '2026-10-14T18:05:00Z',
  errors: {},
}

const daily = (id: string, name: string, unit: string, last: string, vals: number[]): Series => ({
  id, theme: 'ambiente-energia', name, unit, source: 'IDEAM', sourceUrl: 'https://x', periodicity: 'diaria',
  fetchedAt: null,
  points: vals.map((value, i) => {
    const d = new Date(Date.parse(last + 'T00:00:00Z') - (vals.length - 1 - i) * 86400000)
    return { date: d.toISOString().slice(0, 10), value }
  }),
  freshness: { lastDataDate: last, ageDays: 3, staleAfterDays: 7, stale: false },
})

const data: IndicatorsResponse = {
  themes: [
    {
      id: 'ambiente-energia', name: 'Ambiente y energía', series: [
        daily('temperatura-nacional', 'Temperatura del aire (promedio nacional diario)', '°C', '2026-10-13', [20, 22, 24, 21.1]),
        daily('energia-precio-bolsa', 'Precio de bolsa de energía (promedio diario)', 'COP/kWh', '2026-10-11', [700, 750, 762]),
      ],
    },
  ],
  updatedAt: null,
}

const renderToday = (l: LiveSnapshot | null, d: IndicatorsResponse = data) =>
  render(
    <MemoryRouter>
      <Today live={l} data={d} now={NOW} />
    </MemoryRouter>,
  )

describe('Today panel', () => {
  it('shows the dollar with change vs. opening and the live badge when the last tick is recent', () => {
    renderToday(live)
    const card = screen.getByRole('article', { name: /Dólar/i })
    expect(within(card).getByLabelText(/Dólar: 3\.197,56/)).toBeInTheDocument()
    expect(within(card).getByText(/-0,08 %/)).toBeInTheDocument()
    expect(within(card).getByText('en vivo')).toBeInTheDocument()
  })

  it('does not claim "en vivo" when the last tick is hours old (market closed)', () => {
    renderToday({ ...live, usdCop: { ...live.usdCop!, lastTickAt: '2026-10-14T13:00:00Z' } })
    const card = screen.getByRole('article', { name: /Dólar/i })
    expect(within(card).queryByText('en vivo')).toBeNull()
    expect(within(card).getByText(/último dato/i)).toBeInTheDocument()
  })

  it('a missing dollar source says "sin dato" and the other cards still render', () => {
    renderToday({ ...live, usdCop: null })
    expect(within(screen.getByRole('article', { name: /Dólar/i })).getByText(/sin dato/i)).toBeInTheDocument()
    expect(screen.getByRole('article', { name: /sismo/i })).toBeInTheDocument()
    expect(screen.getByRole('article', { name: /Temperatura/i })).toBeInTheDocument()
    expect(screen.getByRole('article', { name: /Precio de bolsa/i })).toBeInTheDocument()
  })

  it('with no live snapshot at all every live card degrades without breaking the daily ones', () => {
    renderToday(null)
    expect(within(screen.getByRole('article', { name: /Dólar/i })).getByText(/sin dato/i)).toBeInTheDocument()
    expect(within(screen.getByRole('article', { name: /sismo/i })).getByText(/sin dato/i)).toBeInTheDocument()
    expect(screen.getByRole('article', { name: /Temperatura/i })).toBeInTheDocument()
  })

  it('shows the latest quake and counts only the last 7 days', () => {
    renderToday(live)
    const card = screen.getByRole('article', { name: /sismo/i })
    expect(within(card).getByText(/M 4,8/)).toBeInTheDocument()
    expect(within(card).getByText(/Cepitá/)).toBeInTheDocument()
    expect(within(card).getByText(/2 sismos/)).toBeInTheDocument() // 13-oct y 10-oct; el de septiembre no cuenta
  })

  it('a usgs error keeps the stored quakes but warns they may be outdated', () => {
    renderToday({ ...live, errors: { usgs: 'La fuente no respondió.' } })
    const card = screen.getByRole('article', { name: /sismo/i })
    expect(within(card).queryByText('en vivo')).toBeNull()
    expect(within(card).getByText(/no se pudo actualizar/i)).toBeInTheDocument()
  })

  it('shows the real date of the XM price, never "ayer", and the age in days', () => {
    renderToday(live)
    const card = screen.getByRole('article', { name: /Precio de bolsa/i })
    expect(within(card).getByText(/2026-10-11/)).toBeInTheDocument()
    expect(within(card).getByText(/hace 3 días/)).toBeInTheDocument()
    expect(within(card).queryByText(/ayer/i)).toBeNull()
    expect(within(card).queryByText('en vivo')).toBeNull()
  })

  it('compares the national temperature with its 30-day average and dates it', () => {
    renderToday(live)
    const card = screen.getByRole('article', { name: /Temperatura/i })
    expect(within(card).getByLabelText(/21,1 °C/)).toBeInTheDocument()
    expect(within(card).getByText(/promedio de los últimos 4 días: 21,78/)).toBeInTheDocument()
    expect(within(card).getByText(/2026-10-13/)).toBeInTheDocument()
  })

  it('a daily series that is not loaded yet says "sin dato" instead of crashing', () => {
    renderToday(live, { themes: [{ id: 'ambiente-energia', name: 'A', series: [] }], updatedAt: null })
    expect(within(screen.getByRole('article', { name: /Temperatura/i })).getByText(/sin dato/i)).toBeInTheDocument()
  })
})
