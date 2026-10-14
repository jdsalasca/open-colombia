import { Link, useParams } from 'react-router-dom'
import { fmt } from '../format'
import { LineChart } from '../paper/LineChart'
import { PaperCard } from '../paper/PaperCard'
import { PaperSheet } from '../paper/PaperSheet'
import type { IndicatorsResponse, Insight } from '../types'
import { InsightBlock } from './Proposals'

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
          <PaperSheet key={s.id} as="section" className="chart-sheet">
            <h3 style={{ marginTop: 0 }}>{s.name} <span className="card-unit">({s.unit})</span></h3>
            <LineChart points={s.points} unit={s.unit} />
            <p className="chip">
              {s.points.length} datos · mín {fmt(Math.min(...s.points.map((p) => p.value)))} · máx{' '}
              {fmt(Math.max(...s.points.map((p) => p.value)))}
            </p>
          </PaperSheet>
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
