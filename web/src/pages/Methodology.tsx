import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getMethodology } from '../api'
import { PaperSheet } from '../paper/PaperSheet'
import type { Methodology as M } from '../types'

export function Methodology() {
  const { id = '' } = useParams()
  const [m, setM] = useState<M | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let live = true
    setM(null)
    setError(false)
    getMethodology(id)
      .then((r) => live && setM(r))
      .catch(() => live && setError(true))
    return () => {
      live = false
    }
  }, [id])

  if (error) {
    return (
      <PaperSheet>
        <p>No encontramos esa metodología. <Link to="/metodologia">Ver todos los indicadores</Link></p>
      </PaperSheet>
    )
  }
  if (!m) return <p>Cargando…</p>
  return (
    <PaperSheet as="article" color="cream">
      <h2 style={{ marginTop: 0 }}>{m.name}</h2>
      <p className="label">Qué mide</p>
      <p>{m.description}</p>
      <p className="label">Cómo se calcula aquí</p>
      <p>{m.method}</p>
      <p className="label">Dónde verificarlo</p>
      <ul className="links">
        <li>Fuente ({m.source}): <a href={m.sourceUrl} target="_blank" rel="noreferrer">{m.sourceUrl}</a></li>
        <li>
          Consulta exacta que hace esta app:{' '}
          <a href={m.queryUrl} target="_blank" rel="noreferrer">{m.queryUrl}</a>
        </li>
        {m.queryBody && (
          <li>
            Esta fuente se consulta por <strong>POST</strong> con este cuerpo (endpoint público sin contrato oficial):
            <pre className="codeblock"><code>{m.queryBody}</code></pre>
          </li>
        )}
        <li>Los datos que mostramos (JSON): <a href={m.seriesUrl}>{m.seriesUrl}</a></li>
      </ul>
      <p className="chip">
        Periodicidad: {m.periodicity} · Unidad: {m.unit} ·{' '}
        {m.higherIsBetter === null ? 'Subir no es bueno ni malo por sí mismo.' : m.higherIsBetter ? 'Un valor mayor es mejor.' : 'Un valor menor es mejor.'}
      </p>
      <p><Link to="/metodologia">← Todos los indicadores y cómo leer las tasas</Link></p>
    </PaperSheet>
  )
}
