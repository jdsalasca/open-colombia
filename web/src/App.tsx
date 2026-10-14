import { useCallback, useEffect, useState } from 'react'
import { BrowserRouter, NavLink, Route, Routes } from 'react-router-dom'
import { getIndicators, getInsights } from './api'
import { RefreshButton } from './components/RefreshButton'
import { PaperSheet } from './paper/PaperSheet'
import { Home } from './pages/Home'
import { Proposals } from './pages/Proposals'
import { ThemePage } from './pages/ThemePage'
import type { IndicatorsResponse, Insight } from './types'

export default function App() {
  const [data, setData] = useState<IndicatorsResponse | null>(null)
  const [insights, setInsights] = useState<Insight[]>([])
  const [error, setError] = useState(false)

  const load = useCallback(async () => {
    try {
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
              <Route path="/" element={<Home data={data} />} />
              <Route path="/tema/:id" element={<ThemePage data={data} insights={insights} />} />
              <Route path="/propuestas" element={<Proposals insights={insights} />} />
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
