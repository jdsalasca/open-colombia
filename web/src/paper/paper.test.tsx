import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Gauge } from './Gauge'
import { LineChart } from './LineChart'
import { PaperCard } from './PaperCard'
import { Sparkline } from './Sparkline'
import type { Series } from '../types'

const base: Series = {
  id: 'x', theme: 'economia', name: 'Desempleo', unit: '%', source: 'Banco Mundial',
  sourceUrl: 'https://example.org', periodicity: 'anual', points: [], fetchedAt: '2026-10-13T00:00:00Z',
}

describe('PaperCard', () => {
  it('shows "sin dato" and no number when the series is empty', () => {
    render(<PaperCard series={base} />)
    expect(screen.getByText('sin dato')).toBeInTheDocument()
    expect(screen.queryByText(/NaN/)).toBeNull()
  })

  it('shows the latest value with source and period', () => {
    const s = { ...base, points: [{ date: '2023-01-01', value: 9 }, { date: '2024-01-01', value: 8.5 }] }
    render(<PaperCard series={s} />)
    expect(screen.getByLabelText(/Desempleo: 8,5/)).toBeInTheDocument()
    expect(screen.getByText('Banco Mundial')).toBeInTheDocument()
    expect(screen.getByText(/2024/)).toBeInTheDocument()
  })
})

describe('charts degrade gracefully', () => {
  it('Sparkline with 0 or 1 point has no path and no NaN', () => {
    const { container, rerender } = render(<Sparkline points={[]} />)
    expect(container.querySelector('path')).toBeNull()
    rerender(<Sparkline points={[{ date: '2024-01-01', value: 3 }]} />)
    expect(container.querySelector('path')).toBeNull()
  })

  it('Sparkline with constant values has finite path', () => {
    const pts = [1, 2, 3].map((i) => ({ date: `202${i}-01-01`, value: 5 }))
    const { container } = render(<Sparkline points={pts} />)
    expect(container.querySelector('path')?.getAttribute('d')).not.toMatch(/NaN/)
  })

  it('LineChart with one point shows a message', () => {
    render(<LineChart points={[{ date: '2024-01-01', value: 1 }]} unit="%" />)
    expect(screen.getByText(/sin datos suficientes/)).toBeInTheDocument()
  })

  it('Gauge clamps and tolerates a degenerate range', () => {
    const { container } = render(<Gauge value={50} min={10} max={10} label="x" />)
    expect(container.innerHTML).not.toMatch(/NaN/)
  })
})
