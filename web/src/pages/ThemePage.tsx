import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getSeries } from '../api'
import { RangeSelector, defaultRange, rangesFor } from '../components/RangeSelector'
import { RatesPanel } from '../components/RatesPanel'
import { ols } from '../format'
import { LineChart } from '../paper/LineChart'
import { YoYBars } from '../paper/YoYBars'
import { Breakdowns } from './Breakdowns'
import { Panorama } from './Panorama'
import { HeatStrip } from '../paper/HeatStrip'
import { Histogram } from '../paper/Histogram'
import { QuakeScatter } from '../paper/QuakeScatter'
import { PaperCard } from '../paper/PaperCard'
import { PaperSheet } from '../paper/PaperSheet'
import type { IndicatorsResponse, Insight, LiveSnapshot, Series, SeriesResponse } from '../types'
import { InsightBlock } from './Proposals'

/** Gráfica de una serie con selector de rango y tasas; consulta /api/series al cambiar el rango. */
function SeriesBlock({ series }: { series: Series }) {
  const [range, setRange] = useState(defaultRange(series.periodicity))
  const [res, setRes] = useState<SeriesResponse | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let live = true
    setError(false)
    setRes(null) // no mostrar datos ni tasas del rango anterior mientras carga el nuevo
    getSeries(series.id, range)
      .then((r) => live && setRes(r))
      .catch(() => live && setError(true))
    return () => {
      live = false
    }
  }, [series.id, range])

  return (
    <PaperSheet as="section" className="chart-sheet">
      <h3 style={{ marginTop: 0 }}>
        {series.name} <span className="card-unit">({series.unit})</span>
      </h3>
      <RangeSelector ranges={rangesFor(series.periodicity)} value={range} onChange={setRange} />
      {error ? (
        <p className="card-empty">No pudimos cargar este rango. Prueba otro.</p>
      ) : !res ? (
        <p className="card-empty" role="status">Cargando…</p>
      ) : (
        <>
          <LineChart points={res.series.points} unit={series.unit} trend={ols(res.series.points)} />
          {series.periodicity === 'anual' && (
            <YoYBars points={res.series.points} higherIsBetter={res.higherIsBetter} unit={series.unit} />
          )}
          <RatesPanel rates={res.rates} unit={series.unit} periodicity={series.periodicity} />
        </>
      )}
      <p className="chip">
        <Link to={`/metodologia/${series.id}`}>¿cómo se calcula?</Link>
      </p>
    </PaperSheet>
  )
}

export function ThemePage({ data, insights, live = null }: { data: IndicatorsResponse; insights: Insight[]; live?: LiveSnapshot | null }) {
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
      <div className="chart-grid">
        {theme.series
          .filter((s) => s.points.length >= 2)
          .map((s) => (
            <SeriesBlock key={s.id} series={s} />
          ))}
      </div>
      {theme.id === 'ambiente-energia' && <Climate series={theme.series} live={live} />}
      {theme.id === 'seguridad' && <Panorama data={data} />}
      {theme.id === 'seguridad' && <Breakdowns />}
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

/** Clima diario (tira de calor) y sismos recientes (histograma + dispersión), solo con lo que haya cargado. */
function Climate({ series, live }: { series: Series[]; live: LiveSnapshot | null }) {
  const daily = series.filter((s) => s.periodicity === 'diaria' && (s.id.startsWith('temperatura') || s.id.startsWith('precipitacion')))
  const quakes = live?.quakes ?? []
  return (
    <>
      {daily.map((s) => (
        <PaperSheet key={s.id} as="section" className="chart-sheet">
          <h3 style={{ marginTop: 0 }}>{s.name}: últimos 90 días</h3>
          <HeatStrip points={s.points} unit={s.unit} days={90} label={s.name} />
        </PaperSheet>
      ))}
      <PaperSheet as="section" className="chart-sheet">
        <h3 style={{ marginTop: 0 }}>Sismos en Colombia (M ≥ 2,5, últimos 30 días)</h3>
        {live === null ? (
          <p className="card-empty">sin datos de sismos por ahora</p>
        ) : (
          <>
            <Histogram values={quakes.map((q) => q.mag)} binWidth={0.5} unit="M" caption="Sismos por magnitud" />
            <QuakeScatter quakes={quakes} />
          </>
        )}
        <p className="chip">Fuente: USGS (catálogo sísmico). «En vivo» se actualiza cada 15 minutos.</p>
      </PaperSheet>
    </>
  )
}
