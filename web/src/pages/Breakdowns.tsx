import { useEffect, useState } from 'react'
import { getBreakdown } from '../api'
import { RangeSelector } from '../components/RangeSelector'
import { HBarChart } from '../paper/HBarChart'
import { PaperSheet } from '../paper/PaperSheet'
import { PieChart } from '../paper/PieChart'
import type { Breakdown } from '../types'

const DIMS = ['sexo', 'zona', 'arma', 'departamento']
const RANGES = ['3m', '6m', '1a', '2a']

/** Composición de homicidios (datos.gov.co): tortas para pocas categorías, barras para departamentos. */
export function Breakdowns() {
  const [dim, setDim] = useState('sexo')
  const [range, setRange] = useState('1a')
  const [data, setData] = useState<Breakdown | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let live = true
    setError(false)
    setData(null)
    getBreakdown(dim, range)
      .then((r) => live && setData(r))
      .catch(() => live && setError(true))
    return () => {
      live = false
    }
  }, [dim, range])

  return (
    <PaperSheet as="section" color="kraft" className="chart-sheet">
      <h3 style={{ marginTop: 0 }}>Homicidios: ¿quiénes y dónde?</h3>
      <RangeSelector ranges={DIMS} value={dim} onChange={setDim} />
      <RangeSelector ranges={RANGES} value={range} onChange={setRange} />
      {error && <p className="card-empty">No pudimos cargar la composición ahora. Intenta de nuevo más tarde.</p>}
      {!error && !data && <p>Cargando…</p>}
      {data && (
        <>
          {dim === 'departamento' ? (
            <HBarChart items={data.items} caption={`Homicidios por ${dim}`} />
          ) : (
            <PieChart items={data.items} caption={`Homicidios por ${dim}`} />
          )}
          <p className="chip">
            Desde {data.from} · total {data.total.toLocaleString('es-CO')} casos · Fuente:{' '}
            <a href={data.queryUrl} target="_blank" rel="noreferrer">consulta a datos.gov.co</a>
          </p>
        </>
      )}
    </PaperSheet>
  )
}
