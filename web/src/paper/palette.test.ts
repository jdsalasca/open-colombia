import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { PALETTE, contrast } from './palette'

// vitest sustituye los .css por cadenas vacías; se lee el archivo real
const css = readFileSync(join(process.cwd(), 'src/paper/paper.css'), 'utf-8')

const sources = import.meta.glob('./*.tsx', { query: '?raw', import: 'default', eager: true }) as Record<
  string,
  string
>

describe('contrast()', () => {
  it('matches the known WCAG ratio for ink on paper', () => {
    expect(contrast('#2b2118', '#f7f0e1')).toBeGreaterThan(13.8)
    expect(contrast('#2b2118', '#f7f0e1')).toBeLessThan(14.0)
  })

  it('is symmetric and 1 for equal colors', () => {
    expect(contrast('#336699', '#336699')).toBeCloseTo(1, 5)
    expect(contrast('#ffffff', '#000000')).toBeCloseTo(21, 3)
    expect(contrast('#0b4f8a', '#f7f0e1')).toBeCloseTo(contrast('#f7f0e1', '#0b4f8a'), 9)
  })
})

describe('palette meets WCAG AA on paper', () => {
  it('every categorical and semantic color has at least 4.5:1', () => {
    for (const c of [...PALETTE.categorical, PALETTE.good, PALETTE.bad, PALETTE.neutral]) {
      expect(contrast(c, PALETTE.paper), c).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('text colors have at least 4.5:1 on paper and on the page board', () => {
    expect(contrast(PALETTE.ink, PALETTE.paper)).toBeGreaterThanOrEqual(4.5)
    expect(contrast(PALETTE.inkSoft, PALETTE.paper)).toBeGreaterThanOrEqual(4.5)
    expect(contrast(PALETTE.inkSoft, PALETTE.board)).toBeGreaterThanOrEqual(4.5)
  })

  it('flag yellow is only a background: fails as a chart color, passes with ink text', () => {
    expect(contrast(PALETTE.yellow, PALETTE.paper)).toBeLessThan(3)
    expect(contrast(PALETTE.ink, PALETTE.yellow)).toBeGreaterThanOrEqual(4.5)
  })

  it('white text on the colored sheets has at least 4.5:1', () => {
    for (const bg of [PALETTE.neutral, PALETTE.bad, PALETTE.good]) {
      expect(contrast('#ffffff', bg), bg).toBeGreaterThanOrEqual(4.5)
    }
  })
})

describe('paper.css tokens', () => {
  const root = css.match(/:root\s*\{([\s\S]*?)\}/)![1]
  const token = (name: string) => root.match(new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`))?.[1]?.toLowerCase()

  it('declares every palette token with the exact palette value', () => {
    PALETTE.categorical.forEach((c, i) => expect(token(`cat-${i + 1}`), `--cat-${i + 1}`).toBe(c))
    expect(token('good')).toBe(PALETTE.good)
    expect(token('bad')).toBe(PALETTE.bad)
    expect(token('neutral')).toBe(PALETTE.neutral)
    expect(token('paper')).toBe(PALETTE.paper)
    expect(token('ink')).toBe(PALETTE.ink)
    expect(token('ink-soft')).toBe(PALETTE.inkSoft)
  })

  it('has no :root color that fails 3:1 against paper, except flag yellow, board, kraft and paper', () => {
    const skip = new Set(['paper', 'yellow', 'board', 'kraft'])
    for (const m of root.matchAll(/--([\w-]+):\s*(#[0-9a-fA-F]{6})/g)) {
      if (skip.has(m[1])) continue
      expect(contrast(m[2], PALETTE.paper), `--${m[1]}`).toBeGreaterThanOrEqual(3)
    }
  })
})

describe('charts use tokens, not raw colors', () => {
  it('no hex color literal in src/paper/*.tsx', () => {
    for (const [file, text] of Object.entries(sources)) {
      expect(text.match(/#[0-9a-fA-F]{6}\b/g) ?? [], file).toEqual([])
    }
  })
})
