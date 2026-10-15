import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { IndicatorsResponse, Methodology, Series, SeriesResponse } from '../types'
import { Methodology as MethodologyPage } from '../pages/Methodology'
import { ThemePage } from '../pages/ThemePage'
import { RangeSelector } from './RangeSelector'
import { RatesPanel } from './RatesPanel'

afterEach(() => vi.unstubAllGlobals())

describe('RangeSelector', () => {
  it('renders only the given ranges and marks the active one', async () => {
    const onChange = vi.fn()
    render(<RangeSelector ranges={['5a', '10a', 'todo']} value="10a" onChange={onChange} />)
    expect(screen.getAllByRole('button')).toHaveLength(3)
    expect(screen.getByRole('button', { name: '10a' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '5a' })).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(screen.getByRole('button', { name: '5a' }))
    expect(onChange).toHaveBeenCalledWith('5a')
  })
})

describe('RatesPanel', () => {
  it('shows "insuficiente" for null rates, without NaN', () => {
    const { container } = render(<RatesPanel rates={null} unit="%" />)
    expect(screen.getByText(/insuficiente/i)).toBeInTheDocument()
    expect(container.textContent).not.toMatch(/NaN/)
  })

  it('labels the change of a percentage series in percentage points', () => {
    render(
      <RatesPanel
        unit="%"
        rates={{
          n: 10, from: '2016-01-01', to: '2025-01-01', change: -0.67, changePct: -7.5, annualizedPct: null, mean: 9,
          min: { date: '2016-01-01', value: 8 }, max: { date: '2020-01-01', value: 12 }, stdDev: 1.2,
        }}
        periodicity="anual"
      />,
    )
    expect(screen.getByText('Variación').nextElementSibling).toHaveTextContent('-0,67 pp')
  })

  it('shows the window dates and omits the relative change when it is null', () => {
    render(
      <RatesPanel
        unit="%"
        rates={{
          n: 6, from: '2021-01-01', to: '2025-01-01', change: -8.16, changePct: null, annualizedPct: null, mean: 3,
          min: { date: '2021-01-01', value: 1 }, max: { date: '2023-01-01', value: 5 }, stdDev: 1,
        }}
        periodicity="anual"
      />,
    )
    expect(screen.getByText(/2021.*2025/)).toBeInTheDocument()
    expect(screen.getByText('Variación').nextElementSibling).not.toHaveTextContent('(—)')
  })

  it('shows a dash for a null annualized rate and the dates of min and max', () => {
    render(
      <RatesPanel
        unit="COP"
        rates={{
          n: 6, from: '2026-03-01', to: '2026-08-01', change: 50, changePct: 1.6, annualizedPct: null, mean: 3100,
          min: { date: '2026-03-01', value: 3050 }, max: { date: '2026-08-01', value: 3200 }, stdDev: 40,
        }}
      />,
    )
    expect(screen.getByText('Anualizada').nextElementSibling).toHaveTextContent('—')
    expect(screen.getByText('Mínimo').nextElementSibling).toHaveTextContent('mar 2026')
    expect(screen.getByText('Máximo').nextElementSibling).toHaveTextContent('ago 2026')
  })
})

const meth: Methodology = {
  id: 'trm', name: 'Tasa de cambio', theme: 'economia', description: 'Pesos por dólar.',
  method: 'Promedio mensual de la TRM diaria.', source: 'datos.gov.co', sourceUrl: 'https://www.datos.gov.co/d/32sa-8pi3',
  queryUrl: 'https://www.datos.gov.co/resource/32sa-8pi3.json?$select=a&$limit=5000', queryBody: null, seriesUrl: '/api/series/trm',
  periodicity: 'mensual', unit: 'COP/USD', higherIsBetter: null,
}

describe('Methodology page for POST sources', () => {
  it('shows the JSON body when the source is queried with POST', async () => {
    const xm = { ...meth, id: 'energia-precio-bolsa', source: 'XM', queryUrl: 'https://servapibi.xm.com.co/hourly',
      queryBody: '{"MetricId":"PrecBolsNaci","StartDate":"2026-06-16"}' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(xm), { status: 200 })))
    render(
      <MemoryRouter initialEntries={['/metodologia/energia-precio-bolsa']}>
        <Routes>
          <Route path="/metodologia/:id" element={<MethodologyPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/"MetricId":"PrecBolsNaci"/)).toBeInTheDocument()
    expect(screen.getByText(/POST/)).toBeInTheDocument()
  })

  it('does not mention POST for GET sources', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...meth, queryBody: null }), { status: 200 })))
    render(
      <MemoryRouter initialEntries={['/metodologia/trm']}>
        <Routes>
          <Route path="/metodologia/:id" element={<MethodologyPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText(/Promedio mensual de la TRM diaria/)
    expect(screen.queryByText(/por POST/)).toBeNull()
  })
})

describe('Methodology page', () => {
  it('shows the exact query url as a link', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(meth), { status: 200 })))
    render(
      <MemoryRouter initialEntries={['/metodologia/trm']}>
        <Routes>
          <Route path="/metodologia/:id" element={<MethodologyPage />} />
        </Routes>
      </MemoryRouter>,
    )
    const link = await screen.findByRole('link', { name: meth.queryUrl })
    expect(link).toHaveAttribute('href', meth.queryUrl)
    expect(screen.getByText(/Promedio mensual de la TRM diaria/)).toBeInTheDocument()
  })
})

const monthly: Series = {
  id: 'trm', theme: 'economia', name: 'TRM', unit: 'COP/USD', source: 'datos.gov.co', sourceUrl: 'u',
  periodicity: 'mensual', fetchedAt: null,
  points: Array.from({ length: 24 }, (_, i) => ({
    date: `${2025 + Math.floor(i / 12)}-${String((i % 12) + 1).padStart(2, '0')}-01`, value: 3000 + i * 10,
  })),
}
const data: IndicatorsResponse = { themes: [{ id: 'economia', name: 'Economía', series: [monthly] }], updatedAt: null }

describe('ThemePage ranges', () => {
  it('re-queries the series with the chosen range', async () => {
    const respond = (range: string): SeriesResponse => ({
      series: monthly, range, availableRanges: ['3m', '6m', '1a', '2a', 'todo'], higherIsBetter: null,
      rates: { n: 6, from: '2026-03-01', to: '2026-08-01', change: 50, changePct: 1.6, annualizedPct: null,
        mean: 3100, min: { date: '2026-03-01', value: 3050 }, max: { date: '2026-08-01', value: 3200 }, stdDev: 40 },
    })
    const fetchMock = vi.fn().mockImplementation((url: string) =>
      Promise.resolve(new Response(JSON.stringify(respond(new URL(url, 'http://x').searchParams.get('range') ?? 'todo')), { status: 200 })))
    vi.stubGlobal('fetch', fetchMock)
    render(
      <MemoryRouter initialEntries={['/tema/economia']}>
        <Routes>
          <Route path="/tema/:id" element={<ThemePage data={data} insights={[]} />} />
        </Routes>
      </MemoryRouter>,
    )
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/series/trm?range=1a'))
    await userEvent.click(screen.getByRole('button', { name: '6m' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/series/trm?range=6m'))
  })

  it('offers only annual ranges for annual series', async () => {
    const annual = { ...monthly, id: 'desempleo', periodicity: 'anual',
      points: Array.from({ length: 12 }, (_, i) => ({ date: `${2010 + i}-01-01`, value: 10 - i * 0.2 })) }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      series: annual, range: 'todo', availableRanges: ['5a', '10a', 'todo'], rates: null, higherIsBetter: false }), { status: 200 })))
    render(
      <MemoryRouter initialEntries={['/tema/economia']}>
        <Routes>
          <Route path="/tema/:id" element={<ThemePage
            data={{ themes: [{ id: 'economia', name: 'Economía', series: [annual] }], updatedAt: null }} insights={[]} />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByRole('button', { name: '10a' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '3m' })).toBeNull()
  })
})
