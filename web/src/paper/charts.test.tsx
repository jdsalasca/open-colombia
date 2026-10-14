import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Breakdowns } from '../pages/Breakdowns'
import type { Breakdown, Point } from '../types'
import { HBarChart } from './HBarChart'
import { LineChart } from './LineChart'
import { PieChart } from './PieChart'
import { YoYBars } from './YoYBars'

afterEach(() => vi.unstubAllGlobals())

const yearly = (vals: number[]): Point[] =>
  vals.map((value, i) => ({ date: `${2010 + i}-01-01`, value }))

describe('PieChart', () => {
  it('shows "sin datos" with no items', () => {
    render(<PieChart items={[]} />)
    expect(screen.getByText(/sin datos/i)).toBeInTheDocument()
  })

  it('draws a full circle for a single category, without NaN', () => {
    const { container } = render(<PieChart items={[{ label: 'URBANA', value: 5, share: 1 }]} />)
    expect(container.querySelector('circle.pie-full')).not.toBeNull()
    expect(container.innerHTML).not.toMatch(/NaN/)
  })

  it('draws one slice per category and a data table', () => {
    const items = [
      { label: 'A', value: 60, share: 0.6 },
      { label: 'B', value: 30, share: 0.3 },
      { label: 'C', value: 10, share: 0.1 },
    ]
    const { container } = render(<PieChart items={items} />)
    expect(container.querySelectorAll('path.slice')).toHaveLength(3)
    expect(container.querySelector('details table')).not.toBeNull()
    expect(container.innerHTML).not.toMatch(/NaN/)
  })
})

describe('HBarChart', () => {
  it('orders bars descending and its table has one row per item', () => {
    const items = [
      { label: 'B', value: 10, share: 0.25 },
      { label: 'A', value: 30, share: 0.75 },
    ]
    const { container } = render(<HBarChart items={items} />)
    const labels = Array.from(container.querySelectorAll('.hbar-label')).map((e) => e.textContent)
    expect(labels).toEqual(['A', 'B'])
    expect(container.querySelectorAll('details tbody tr')).toHaveLength(2)
  })

  it('keeps the OTROS bucket last even when it is the largest', () => {
    const items = [
      { label: 'OTROS', value: 100, share: 0.7 },
      { label: 'A', value: 30, share: 0.2 },
      { label: 'B', value: 20, share: 0.1 },
    ]
    const { container } = render(<HBarChart items={items} />)
    const labels = Array.from(container.querySelectorAll('.hbar-label')).map((e) => e.textContent)
    expect(labels).toEqual(['A', 'B', 'OTROS'])
  })

  it('shows "sin datos" when empty', () => {
    render(<HBarChart items={[]} />)
    expect(screen.getByText(/sin datos/i)).toBeInTheDocument()
  })
})

describe('YoYBars', () => {
  it('skips a zero base instead of producing Infinity/NaN', () => {
    const { container } = render(<YoYBars points={yearly([0, 5, 10, 8])} higherIsBetter={true} />)
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/)
    expect(container.querySelectorAll('rect.bar')).toHaveLength(2) // 5->10 y 10->8; 0->5 se omite
  })

  it('colors by meaning: a rise is bad when higher is worse, neutral when null', () => {
    const bad = render(<YoYBars points={yearly([10, 12])} higherIsBetter={false} />)
    expect(bad.container.querySelector('rect.bar-bad')).not.toBeNull()
    const neutral = render(<YoYBars points={yearly([10, 12])} higherIsBetter={null} />)
    expect(neutral.container.querySelector('rect.bar-neutral')).not.toBeNull()
  })

  it('renders a message with fewer than two points', () => {
    render(<YoYBars points={yearly([3])} higherIsBetter={true} />)
    expect(screen.getByText(/sin datos suficientes/i)).toBeInTheDocument()
  })
})

describe('LineChart trend', () => {
  it('draws a dashed trend line and legend when a trend is given', () => {
    const pts = yearly([1, 2, 3, 4, 5, 6])
    const { container } = render(<LineChart points={pts} unit="%" trend={{ slope: 1, intercept: 1, r2: 1 }} />)
    expect(container.querySelector('line.trend')).not.toBeNull()
    expect(screen.getByText(/R² = 1/)).toBeInTheDocument()
  })

  it('says the trend is not estimated when null', () => {
    render(<LineChart points={yearly([1, 2, 3])} unit="%" trend={null} />)
    expect(screen.getByText(/tendencia no estimada/i)).toBeInTheDocument()
  })

  it('constant series stays finite', () => {
    const { container } = render(<LineChart points={yearly([5, 5, 5, 5, 5])} unit="%" trend={{ slope: 0, intercept: 5, r2: 0 }} />)
    expect(container.innerHTML).not.toMatch(/NaN|Infinity/)
  })
})

describe('Breakdowns panel', () => {
  const bd: Breakdown = {
    dim: 'sexo', range: '1a', from: '2025-10-13', total: 100, source: 'datos.gov.co', queryUrl: 'https://q',
    items: [{ label: 'MASCULINO', value: 90, share: 0.9 }, { label: 'FEMENINO', value: 10, share: 0.1 }],
  }

  it('loads a breakdown and re-queries when the dimension changes', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify(bd), { status: 200 })))
    vi.stubGlobal('fetch', fetchMock)
    render(<Breakdowns />)
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/breakdowns/homicidios?dim=sexo&range=1a'))
    await userEvent.click(screen.getByRole('button', { name: 'zona' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/breakdowns/homicidios?dim=zona&range=1a'))
  })

  it('shows a clear message when the source is down (502)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'caído' }), { status: 502 })))
    render(<Breakdowns />)
    expect(await screen.findByText(/no pudimos cargar la composición/i)).toBeInTheDocument()
  })
})
