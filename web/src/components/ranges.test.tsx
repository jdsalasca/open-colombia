import { describe, expect, it } from 'vitest'
import { defaultRange, rangesFor } from './RangeSelector'

describe('range helpers mirror the backend per periodicity', () => {
  it('daily series offer 7d, 30d, 90d, 1a and todo, and open on 90 days', () => {
    expect(rangesFor('diaria')).toEqual(['7d', '30d', '90d', '1a', 'todo'])
    expect(defaultRange('diaria')).toBe('90d')
  })

  it('monthly and annual keep their ranges and defaults', () => {
    expect(rangesFor('mensual')).toEqual(['3m', '6m', '1a', '2a', 'todo'])
    expect(defaultRange('mensual')).toBe('1a')
    expect(rangesFor('anual')).toEqual(['5a', '10a', 'todo'])
    expect(defaultRange('anual')).toBe('10a')
  })

  it('every default is one of the ranges offered for that periodicity', () => {
    for (const p of ['diaria', 'mensual', 'anual', 'otra']) expect(rangesFor(p)).toContain(defaultRange(p))
  })
})
