import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RefreshButton } from '../components/RefreshButton'
import type { IndicatorsResponse, Insight, Series } from '../types'
import { Proposals } from './Proposals'
import { ThemePage } from './ThemePage'

afterEach(() => vi.unstubAllGlobals())

const empty: Series = {
  id: 'trm', theme: 'economia', name: 'Tasa de cambio', unit: 'COP/USD', source: 'datos.gov.co',
  sourceUrl: 'https://x', periodicity: 'mensual', points: [], fetchedAt: '2026-10-13T00:00:00Z',
}
const data: IndicatorsResponse = { themes: [{ id: 'economia', name: 'Economía', series: [empty] }], updatedAt: null }

describe('RefreshButton', () => {
  it('calls refresh once on double click and disables while running', async () => {
    let resolve!: (r: Response) => void
    const fetchMock = vi.fn().mockReturnValue(new Promise<Response>((r) => (resolve = r)))
    vi.stubGlobal('fetch', fetchMock)
    const onDone = vi.fn()
    render(<RefreshButton onDone={onDone} />)
    const btn = screen.getByRole('button')
    await userEvent.dblClick(btn)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(btn).toBeDisabled()
    resolve(new Response(JSON.stringify({ at: 'x', results: [{ id: 'a', ok: false, error: 'e' }] }), { status: 200 }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(/Sin respuesta: a/))
    expect(btn).toBeEnabled()
  })

  it('shows the wait when rate limited and re-enables', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 429, headers: { 'Retry-After': '30' } })))
    render(<RefreshButton onDone={() => {}} />)
    await userEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('30 s'))
    expect(screen.getByRole('button')).toBeEnabled()
  })
})

describe('pages', () => {
  it('ThemePage shows "sin dato" for an indicator without points', () => {
    render(
      <MemoryRouter initialEntries={['/tema/economia']}>
        <Routes>
          <Route path="/tema/:id" element={<ThemePage data={data} insights={[]} />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('sin dato')).toBeInTheDocument()
  })

  it('Proposals shows n and R² next to each proposal', () => {
    const ins: Insight[] = [{
      themeId: 'economia', title: 'Crecimiento y desempleo', finding: 'f', literature: 'l', proposal: 'p',
      confidence: { n: 12, r2: 0.734, level: 'media' },
    }]
    render(<Proposals insights={ins} />)
    expect(screen.getByText(/n = 12/)).toHaveTextContent('R² = 0,73')
  })
})
