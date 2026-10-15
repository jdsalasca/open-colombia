import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ChartDefs, patternFill } from './ChartDefs'
import { HBarChart } from './HBarChart'
import { LegendTags } from './LegendTags'
import { PieChart } from './PieChart'
import { YoYBars } from './YoYBars'

describe('patternFill', () => {
  it('returns a distinct url(#pattern) for each of the 7 categories and wraps around', () => {
    const fills = [0, 1, 2, 3, 4, 5, 6].map(patternFill)
    expect(new Set(fills).size).toBe(7)
    expect(patternFill(7)).toBe(patternFill(0))
    expect(patternFill(0)).toMatch(/^url\(#pat-\d\)$/)
  })
})

describe('ChartDefs', () => {
  it('defines one pattern per category plus the semantic good/bad/neutral ones', () => {
    const { container } = render(<ChartDefs />)
    const ids = Array.from(container.querySelectorAll('pattern')).map((p) => p.id)
    for (let i = 0; i < 7; i++) expect(ids).toContain(`pat-${i}`)
    expect(ids).toEqual(expect.arrayContaining(['pat-good', 'pat-bad', 'pat-neutral']))
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })
})

describe('LegendTags', () => {
  it('renders one tag per item with a pattern swatch, the label and the value', () => {
    const { container } = render(
      <LegendTags items={[{ label: 'Hurto', value: '1.200', index: 0 }, { label: 'Extorsión', value: '300', index: 1 }]} />,
    )
    expect(container.querySelectorAll('ul.legend-tags > li')).toHaveLength(2)
    expect(screen.getByText('Hurto')).toBeInTheDocument()
    expect(screen.getByText('1.200')).toBeInTheDocument()
    const fills = Array.from(container.querySelectorAll('li rect')).map((r) => r.getAttribute('fill'))
    expect(new Set(fills).size).toBe(2)
  })

  it('renders nothing for an empty list', () => {
    const { container } = render(<LegendTags items={[]} />)
    expect(container.querySelector('ul')).toBeNull()
  })
})

describe('charts do not rely on color alone', () => {
  it('pie slices use a different pattern each and the legend repeats them', () => {
    const items = [
      { label: 'A', value: 60, share: 0.6 },
      { label: 'B', value: 30, share: 0.3 },
      { label: 'C', value: 10, share: 0.1 },
    ]
    const { container } = render(<PieChart items={items} />)
    const slices = Array.from(container.querySelectorAll('path.slice')).map((p) => p.getAttribute('fill'))
    expect(new Set(slices).size).toBe(3)
    expect(slices.every((f) => f?.startsWith('url(#pat-'))).toBe(true)
    expect(container.querySelectorAll('ul.legend-tags > li')).toHaveLength(3)
  })

  it('horizontal bars carry a per-category pattern class', () => {
    const items = [
      { label: 'A', value: 30, share: 0.6 },
      { label: 'B', value: 20, share: 0.4 },
    ]
    const { container } = render(<HBarChart items={items} />)
    const classes = Array.from(container.querySelectorAll('.hbar-fill')).map((e) => e.className)
    expect(new Set(classes).size).toBe(2)
  })

  it('YoY bars use different textures for good and bad changes', () => {
    const pts = [
      { date: '2020-01-01', value: 10 }, { date: '2021-01-01', value: 12 }, { date: '2022-01-01', value: 9 },
    ]
    const { container } = render(<YoYBars points={pts} higherIsBetter={true} />)
    expect(container.querySelector('rect.bar-good')).toHaveAttribute('fill', 'url(#pat-good)')
    expect(container.querySelector('rect.bar-bad')).toHaveAttribute('fill', 'url(#pat-bad)')
  })
})
