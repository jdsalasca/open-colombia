import { useCallback, useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom'
import { getIndicators, getInsights, getLive } from './api'
import { RefreshButton } from './components/RefreshButton'
import { ChartDefs } from './paper/ChartDefs'
import { PaperSheet } from './paper/PaperSheet'
import { Home } from './pages/Home'
import { Methodology } from './pages/Methodology'
import { MethodologyIndex } from './pages/MethodologyIndex'
import { Proposals } from './pages/Proposals'
import { ThemePage } from './pages/ThemePage'
import type { IndicatorsResponse, Insight, LiveSnapshot } from './types'

export default function App() {
  const [data, setData] = useState<IndicatorsResponse | null>(null)
  const [insights, setInsights] = useState<Insight[]>([])
  const [live, setLive] = useState<LiveSnapshot | null>(null)
  const [error, setError] = useState(false)

  const load = useCallback(async () => {
    try {
      // lo en vivo es opcional: si falla, el resto de la página sigue
      void getLive().then(setLive).catch(() => setLive(null))
      const [d, i] = await Promise.all([getIndicators(), getInsights()])
      setData(d)
      setInsights(i)
      setError(false)
    } catch {
      setError(true)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const updated = data?.updatedAt ? new Date(data.updatedAt).toLocaleString('es-CO') : null

  return (
    <BrowserRouter>
      <ChartDefs />
      <div className="page">
        <header>
          <div className="masthead">
            <div>
              <h1>Open Colombia</h1>
              <p style={{ margin: 0 }}>
                {updated ? `Datos guardados al ${updated}` : 'Aún sin datos guardados'}
              </p>
            </div>
            <RefreshButton onDone={() => void load()} />
          </div>
          <nav aria-label="Principal">
            <ul className="nav">
              <li><NavLink to="/" end>Panorama</NavLink></li>
              {(data?.themes ?? []).map((t) => (
                <li key={t.id}><NavLink to={`/tema/${t.id}`}>{t.name}</NavLink></li>
              ))}
              <li><NavLink to="/propuestas">Qué hacer</NavLink></li>
              <li><NavLink to="/metodologia">Metodología</NavLink></li>
            </ul>
          </nav>
        </header>
        <main>
          {error && (
            <PaperSheet color="red">
              <p>No pudimos cargar los datos del servidor. Intenta recargar la página.</p>
            </PaperSheet>
          )}
          {data && (
            <Routes>
              <Route path="/" element={<Home data={data} live={live} />} />
              <Route path="/tema/:id" element={<ThemePage data={data} insights={insights} live={live} />} />
              <Route path="/propuestas" element={<Proposals insights={insights} />} />
              <Route path="/metodologia" element={<MethodologyIndex />} />
              <Route path="/metodologia/:id" element={<Methodology />} />
            </Routes>
          )}
          {!data && !error && <p>Cargando…</p>}
        </main>
        <footer className="chip">
          Código abierto · Fuentes: datos.gov.co y Banco Mundial · Las propuestas son ilustrativas.
        </footer>
      </div>
    </BrowserRouter>
  )
}
