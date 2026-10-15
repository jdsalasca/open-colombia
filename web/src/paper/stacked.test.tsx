import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { Panorama } from '../pages/Panorama'
import type { IndicatorsResponse, Point, Series } from '../types'
import { SmallMultiples } from './SmallMultiples'
import { StackedBars } from './StackedBars'

describe('StackedBars', () => {
  it('draws one rect per non-zero cell with a different pattern per category', () => {
    const { container } = render(
      <StackedBars
        categories={['Hurto', 'Extorsión', 'Secuestro']}
        periods={['2026-05', '2026-06', '2026-07', '2026-08']}
        values={[[100, 10, 1], [110, 12, 2], [90, 9, 1], [95, 8, 3]]}
      />,
    )
    const rects = Array.from(container.querySelectorAll('rect.stack'))
    expect(rects).toHaveLength(12)
    const fills = new Set(rects.map((r) => r.getAttribute('fill')))
    expect(fills.size).toBe(3)
    expect(Array.from(fills).every((f) => f?.startsWith('url(#pat-'))).toBe(true)
    expect(container.querySelectorAll('ul.legend-tags > li')).toHaveLength(3)
    expect(container.querySelector('details table')).not.toBeNull()
  })

  it('a category of all zeros does not break the scale or produce NaN', () => {
    const { container } = render(
      <StackedBars categories={['A', 'B']} periods={['m1', 'm2']} values={[[5, 0], [7, 0]]} />,
    )
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/)
  })

  it('a missing cell is labelled "sin dato" in the table and drawn with no height', () => {
    const { container } = render(
      <StackedBars categories={['A', 'B']} periods={['m1', 'm2']} values={[[5, 3], [7, null]]} />,
    )
    const table = container.querySelector('details table')!
    expect(within(table as HTMLElement).getByText('sin dato')).toBeInTheDocument()
    expect(container.querySelectorAll('rect.stack')).toHaveLength(3)
  })

  it('with no data at all it says so instead of drawing an empty chart', () => {
    render(<StackedBars categories={['A']} periods={[]} values={[]} />)
    expect(screen.getByText(/sin datos/i)).toBeInTheDocument()
  })
})

describe('SmallMultiples', () => {
  const pts = (vals: number[]): Point[] => vals.map((v, i) => ({ date: `2026-0${i + 1}-01`, value: v }))

  it('renders one mini chart per series with its last value as an end label', () => {
    const { container } = render(
      <SmallMultiples
        series={[
          { label: 'Hurto', points: pts([10, 12, 11, 15]) },
          { label: 'Secuestro', points: pts([3, 2, 4, 1]) },
        ]}
      />,
    )
    expect(container.querySelectorAll('svg.mini')).toHaveLength(2)
    expect(screen.getByText('Hurto')).toBeInTheDocument()
    expect(screen.getByText('Secuestro')).toBeInTheDocument()
    expect(container.querySelectorAll('text.end-label')).toHaveLength(2)
  })

  it('a series with no points shows "sin datos" and never NaN', () => {
    const { container } = render(<SmallMultiples series={[{ label: 'Vacía', points: [] }]} />)
    expect(screen.getByText(/sin datos/i)).toBeInTheDocument()
    expect(container.innerHTML).not.toMatch(/NaN/)
  })

  it('a constant series stays finite', () => {
    const { container } = render(<SmallMultiples series={[{ label: 'Plana', points: pts([5, 5, 5]) }]} />)
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/)
  })
})

const monthly = (id: string, name: string, vals: Array<[string, number]>): Series => ({
  id, theme: 'seguridad', name, unit: 'casos/mes', source: 'datos.gov.co', sourceUrl: 'u', periodicity: 'mensual',
  fetchedAt: null, points: vals.map(([date, value]) => ({ date, value })),
})

describe('Panorama de delitos', () => {
  const months = Array.from({ length: 14 }, (_, i) => {
    const d = new Date(Date.UTC(2025, 5 + i, 1))
    return d.toISOString().slice(0, 10)
  })
  const mk = (id: string, base: number) => monthly(id, id, months.map((m, i) => [m, base + i] as [string, number]))

  it('uses the last 12 common months and the five crime series', () => {
    const data: IndicatorsResponse = {
      themes: [{ id: 'seguridad', name: 'Seguridad', series: [
        mk('hurto-personas', 1000), mk('extorsion', 100), mk('secuestro', 10), mk('delitos-informaticos', 500),
        mk('violencia-intrafamiliar', 800),
      ] }],
      updatedAt: null,
    }
    const { container } = render(<MemoryRouter><Panorama data={data} /></MemoryRouter>)
    expect(container.querySelectorAll('rect.stack')).toHaveLength(12 * 5)
    expect(container.querySelectorAll('svg.mini')).toHaveLength(5)
  })

  it('a missing crime series is reported, not silently dropped', () => {
    const data: IndicatorsResponse = {
      themes: [{ id: 'seguridad', name: 'Seguridad', series: [mk('hurto-personas', 1000)] }],
      updatedAt: null,
    }
    render(<MemoryRouter><Panorama data={data} /></MemoryRouter>)
    expect(screen.getAllByText(/sin dato/i).length).toBeGreaterThan(0)
  })

  it('renders nothing for the section when the theme has no crime data at all', () => {
    const { container } = render(
      <MemoryRouter><Panorama data={{ themes: [{ id: 'seguridad', name: 'Seguridad', series: [] }], updatedAt: null }} /></MemoryRouter>,
    )
    expect(container.querySelector('rect.stack')).toBeNull()
    expect(screen.getByText(/sin datos/i)).toBeInTheDocument()
  })
})
