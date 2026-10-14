import { describe, expect, it } from 'vitest'
import { ols, previous } from './format'
import type { Series } from './types'

const mk = (values: Array<[string, number]>): Series => ({
  id: 'x', theme: 't', name: 'X', unit: '%', source: 's', sourceUrl: 'u', periodicity: 'anual',
  points: values.map(([date, value]) => ({ date, value })), fetchedAt: null,
})

describe('previous', () => {
  it('returns the second-to-last point by date even when unordered', () => {
    const s = mk([['2024-01-01', 3], ['2022-01-01', 1], ['2023-01-01', 2]])
    expect(previous(s)).toEqual({ date: '2023-01-01', value: 2 })
  })

  it('is null with one or zero points', () => {
    expect(previous(mk([['2024-01-01', 3]]))).toBeNull()
    expect(previous(mk([]))).toBeNull()
  })
})

describe('ols', () => {
  const pts = (n: number, f: (i: number) => number) =>
    Array.from({ length: n }, (_, i) => ({ date: `${2000 + i}-01-01`, value: f(i) }))

  it('is null with fewer than 5 points', () => {
    expect(ols(pts(4, (i) => i))).toBeNull()
  })

  it('recovers an exact line', () => {
    const r = ols(pts(6, (i) => 2 * i + 1))!
    expect(r.slope).toBeCloseTo(2, 1)
    expect(r.r2).toBeCloseTo(1, 4)
  })

  it('constant series has no NaN', () => {
    const r = ols(pts(6, () => 5))!
    expect(Number.isNaN(r.r2)).toBe(false)
    expect(r.slope).toBeCloseTo(0, 9)
  })
})
