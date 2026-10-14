import { useRef, useState } from 'react'
import { refresh } from '../api'

interface Props {
  onDone: () => void
}

type State =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'done'; ok: number; total: number; failed: string[] }
  | { kind: 'limited'; seconds: number }
  | { kind: 'error' }

export function RefreshButton({ onDone }: Props) {
  const [state, setState] = useState<State>({ kind: 'idle' })
  const busy = useRef(false) // evita dobles llamadas antes de que React re-renderice

  async function click() {
    if (busy.current) return
    busy.current = true
    setState({ kind: 'running' })
    try {
      const out = await refresh()
      if (out.rateLimited) {
        setState({ kind: 'limited', seconds: out.retryAfter })
      } else {
        const results = out.report.results
        const failed = results.filter((r) => !r.ok).map((r) => r.id)
        setState({ kind: 'done', ok: results.length - failed.length, total: results.length, failed })
        onDone()
      }
    } catch {
      setState({ kind: 'error' })
    } finally {
      busy.current = false
    }
  }

  return (
    <div>
      <button type="button" className="btn" onClick={click} disabled={state.kind === 'running'}>
        {state.kind === 'running' ? 'Actualizando…' : 'Actualizar datos'}
      </button>
      <p className="status" role="status" aria-live="polite">
        {state.kind === 'done' &&
          `Listo: ${state.ok} de ${state.total} fuentes actualizadas.` +
            (state.failed.length ? ` Sin respuesta: ${state.failed.join(', ')} (se conserva el dato anterior).` : '')}
        {state.kind === 'limited' && `Se actualizó hace poco; intenta de nuevo en ${state.seconds} s.`}
        {state.kind === 'error' && 'No se pudo actualizar ahora. Se muestran los últimos datos guardados.'}
      </p>
    </div>
  )
}
