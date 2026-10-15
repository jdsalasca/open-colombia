import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemePage } from '../pages/ThemePage'
import type { IndicatorsResponse, LiveSnapshot, Point, Quake, Series } from '../types'
import { HeatStrip } from './HeatStrip'
import { Histogram, histogramBins } from './Histogram'
import { QuakeScatter } from './QuakeScatter'

afterEach(() => vi.unstubAllGlobals())

const day = (base: string, i: number) =>
  new Date(Date.parse(base + 'T00:00:00Z') + i * 86400000).toISOString().slice(0, 10)

describe('HeatStrip', () => {
  const series = (n: number, skip: number[] = [], f: (i: number) => number = (i) => 20 + (i % 7)): Point[] =>
    Array.from({ length: n }, (_, i) => i)
      .filter((i) => !skip.includes(i))
      .map((i) => ({ date: day('2026-07-16', i), value: f(i) }))

  it('draws one cell per calendar day and marks missing days as gaps', () => {
    const { container } = render(<HeatStrip points={series(90, [10, 11, 40])} unit="°C" days={90} />)
    expect(container.querySelectorAll('.heat-grid .heat-cell')).toHaveLength(90)
    expect(container.querySelectorAll('.heat-grid .heat-cell.gap')).toHaveLength(3)
  })

  it('a gap is never colored like a value: it carries a texture class and says "sin dato"', () => {
    const { container } = render(<HeatStrip points={series(10, [3])} unit="°C" days={10} />)
    const gap = container.querySelector('.heat-grid .heat-cell.gap')!
    expect(gap.className).not.toMatch(/heat-[1-4]/)
    expect(gap.getAttribute('title')).toMatch(/sin dato/)
  })

  it('all equal values use a single intensity and never NaN', () => {
    const { container } = render(<HeatStrip points={series(20, [], () => 5)} unit="mm" days={20} />)
    const classes = new Set(Array.from(container.querySelectorAll('.heat-grid .heat-cell:not(.gap)')).map((c) => c.className))
    expect(classes.size).toBe(1)
    expect(container.innerHTML).not.toMatch(/NaN/)
  })

  it('uses more than one intensity when values differ and lists them in a data table', () => {
    const { container } = render(<HeatStrip points={series(28)} unit="°C" days={28} />)
    const classes = new Set(Array.from(container.querySelectorAll('.heat-grid .heat-cell:not(.gap)')).map((c) => c.className))
    expect(classes.size).toBeGreaterThan(1)
    expect(container.querySelector('details table')).not.toBeNull()
  })

  it('with no points says there is no data', () => {
    render(<HeatStrip points={[]} unit="°C" days={90} />)
    expect(screen.getByText(/sin datos/i)).toBeInTheDocument()
  })
})

describe('Histogram', () => {
  it('bins magnitudes by 0.5 and keeps empty bins in between', () => {
    expect(histogramBins([2.5, 2.6, 3.6], 0.5)).toEqual([
      { from: 2.5, to: 3.0, count: 2 },
      { from: 3.0, to: 3.5, count: 0 },
      { from: 3.5, to: 4.0, count: 1 },
    ])
  })

  it('an empty list says there were no quakes', () => {
    render(<Histogram values={[]} binWidth={0.5} unit="M" />)
    expect(screen.getByText(/sin sismos en el periodo/i)).toBeInTheDocument()
  })

  it('a single value gives one bin and a finite chart', () => {
    const { container } = render(<Histogram values={[3.2]} binWidth={0.5} unit="M" />)
    expect(container.querySelectorAll('rect.hist-bar')).toHaveLength(1)
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/)
  })

  it('has a data table with one row per bin', () => {
    const { container } = render(<Histogram values={[2.5, 3.6]} binWidth={0.5} unit="M" />)
    expect(container.querySelectorAll('details tbody tr')).toHaveLength(3)
  })
})

describe('QuakeScatter', () => {
  const q = (t: string, mag: number, place = 'x'): Quake => ({ time: t, mag, place, lat: 5, lon: -74, depthKm: 10 })

  it('with no quakes shows a message', () => {
    render(<QuakeScatter quakes={[]} />)
    expect(screen.getByText(/sin sismos/i)).toBeInTheDocument()
  })

  it('a single quake is drawn without dividing by zero', () => {
    const { container } = render(<QuakeScatter quakes={[q('2026-10-10T00:00:00Z', 3.4, 'Chocó')]} />)
    expect(container.querySelectorAll('circle.quake')).toHaveLength(1)
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/)
  })

  it('labels the largest event and scales radius with magnitude', () => {
    const { container } = render(
      <QuakeScatter
        quakes={[q('2026-10-01T00:00:00Z', 2.6), q('2026-10-05T00:00:00Z', 4.9, 'Cepitá'), q('2026-10-09T00:00:00Z', 3.0)]}
      />,
    )
    expect(container.querySelector('text.mark-text')?.textContent).toMatch(/M 4,9.*Cepitá/)
    const radii = Array.from(container.querySelectorAll('circle.quake')).map((c) => Number(c.getAttribute('r')))
    expect(Math.max(...radii)).toBeGreaterThan(Math.min(...radii))
  })
})

describe('ThemePage ambiente-energia', () => {
  const temp: Series = {
    id: 'temperatura-nacional', theme: 'ambiente-energia', name: 'Temperatura', unit: '°C', source: 'IDEAM',
    sourceUrl: 'u', periodicity: 'diaria', fetchedAt: null,
    points: Array.from({ length: 30 }, (_, i) => ({ date: day('2026-09-14', i), value: 20 + (i % 5) })),
  }
  const data: IndicatorsResponse = {
    themes: [{ id: 'ambiente-energia', name: 'Ambiente y energía', series: [temp] }], updatedAt: null,
  }
  const live: LiveSnapshot = {
    usdCop: null, fetchedAt: null, errors: {},
    quakes: [{ time: '2026-10-09T00:00:00Z', mag: 3.0, place: 'Chocó', lat: 5, lon: -76, depthKm: 10 }],
  }

  it('shows the heat strip for daily climate series and the quake charts from live data', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    const { container } = render(
      <MemoryRouter initialEntries={['/tema/ambiente-energia']}>
        <Routes>
          <Route path="/tema/:id" element={<ThemePage data={data} insights={[]} live={live} />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(container.querySelectorAll('.heat-grid .heat-cell').length).toBeGreaterThanOrEqual(30)
    expect(container.querySelectorAll('circle.quake')).toHaveLength(1)
    expect(container.querySelectorAll('rect.hist-bar').length).toBeGreaterThan(0)
  })

  it('without live data the quake section says so and the page still renders', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    render(
      <MemoryRouter initialEntries={['/tema/ambiente-energia']}>
        <Routes>
          <Route path="/tema/:id" element={<ThemePage data={data} insights={[]} live={null} />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getAllByText(/sin sismos|sin datos de sismos/i).length).toBeGreaterThan(0)
  })
})
