import { afterEach, describe, expect, it, vi } from 'vitest'
import { refresh } from './api'

afterEach(() => vi.unstubAllGlobals())

describe('refresh', () => {
  it('maps 429 to rateLimited with Retry-After seconds', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 429, headers: { 'Retry-After': '42' } })))
    expect(await refresh()).toEqual({ rateLimited: true, retryAfter: 42 })
  })

  it('returns the report on success', async () => {
    const body = { at: '2026-10-13T00:00:00Z', results: [{ id: 'a', ok: true, error: null }] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 200 })))
    expect(await refresh()).toEqual({ rateLimited: false, report: body })
  })

  it('throws on server error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('x', { status: 500 })))
    await expect(refresh()).rejects.toThrow()
  })
})
