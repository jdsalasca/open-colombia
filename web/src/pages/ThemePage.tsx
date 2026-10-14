import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getSeries } from '../api'
import { RangeSelector, defaultRange, rangesFor } from '../components/RangeSelector'
import { RatesPanel } from '../components/RatesPanel'
import { LineChart } from '../paper/LineChart'
import { PaperCard } from '../paper/PaperCard'
import { PaperSheet } from '../paper/PaperSheet'
import type { IndicatorsResponse, Insight, Series, SeriesResponse } from '../types'
import { InsightBlock } from './Proposals'

/** Gráfica de una serie con selector de rango y tasas; consulta /api/series al cambiar el rango. */
function SeriesBlock({ series }: { series: Series }) {
  const [range, setRange] = useState(defaultRange(series.periodicity))
  const [res, setRes] = useState<SeriesResponse | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let live = true
    setError(false)
    getSeries(series.id, range)
      .then((r) => live && setRes(r))
      .catch(() => live && setError(true))
    return () => {
      live = false
    }
  }, [series.id, range])

  const points = res?.series.points ?? series.points
  return (
    <PaperSheet as="section" className="chart-sheet">
      <h3 style={{ marginTop: 0 }}>
        {series.name} <span className="card-unit">({series.unit})</span>
      </h3>
      <RangeSelector ranges={rangesFor(series.periodicity)} value={range} onChange={setRange} />
      {error ? (
        <p className="card-empty">No pudimos cargar este rango. Prueba otro.</p>
      ) : (
        <>
          <LineChart points={points} unit={series.unit} />
          <RatesPanel rates={res?.rates ?? null} unit={series.unit} periodicity={series.periodicity} />
        </>
      )}
      <p className="chip">
        <Link to={`/metodologia/${series.id}`}>¿cómo se calcula?</Link>
      </p>
    </PaperSheet>
  )
}

export function ThemePage({ data, insights }: { data: IndicatorsResponse; insights: Insight[] }) {
  const { id } = useParams()
  const theme = data.themes.find((t) => t.id === id)
  if (!theme) {
    return (
      <PaperSheet>
        <p>No encontramos ese tema. <Link to="/">Volver al inicio</Link></p>
      </PaperSheet>
    )
  }
  const mine = insights.filter((i) => i.themeId === theme.id)
  return (
    <>
      <h2>{theme.name}</h2>
      <div className="grid">
        {theme.series.map((s) => (
          <PaperCard key={s.id} series={s} />
        ))}
      </div>
      {theme.series
        .filter((s) => s.points.length >= 2)
        .map((s) => (
          <SeriesBlock key={s.id} series={s} />
        ))}
      {mine.length > 0 && (
        <>
          <h3>Qué dicen los datos</h3>
          {mine.map((i) => (
            <InsightBlock key={i.title} insight={i} />
          ))}
        </>
      )}
    </>
  )
}
