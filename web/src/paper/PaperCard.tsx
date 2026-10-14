import { Link } from 'react-router-dom'
import { delta, fmt, latest, periodLabel, previous } from '../format'
import type { Series } from '../types'
import { PaperSheet } from './PaperSheet'
import { Sparkline } from './Sparkline'
import { SourceChip } from '../components/SourceChip'

interface Props {
  series: Series
  color?: 'cream' | 'yellow' | 'blue' | 'red' | 'green' | 'kraft'
  tilt?: number
}

/** Tarjeta de indicador: cifra grande, variación, sparkline y fuente. Sin datos → "sin dato". */
export function PaperCard({ series, color = 'cream', tilt = 0 }: Props) {
  const last = latest(series)
  const d = delta(series)
  const prev = previous(series)
  return (
    <PaperSheet as="article" color={color} tilt={tilt} className="card">
      <h3 className="card-title">{series.name}</h3>
      {last ? (
        <>
          <p className="card-value" aria-label={`${series.name}: ${fmt(last.value)} ${series.unit}`}>
            {fmt(last.value)} <span className="card-unit">{series.unit}</span>
          </p>
          {d !== null && (
            <p className={`card-delta ${d > 0 ? 'up' : d < 0 ? 'down' : ''}`}>
              {d > 0 ? '▲' : d < 0 ? '▼' : '■'} {fmt(Math.abs(d))} vs.{' '}
              {prev ? `${periodLabel(prev.date, series.periodicity)}: ${fmt(prev.value)} ${series.unit}` : 'dato anterior'}
            </p>
          )}
          <Sparkline points={series.points} />
        </>
      ) : (
        <p className="card-empty">sin dato</p>
      )}
      <p className="chip">
        <Link to={`/metodologia/${series.id}`}>¿cómo se calcula?</Link>
      </p>
      <SourceChip series={series} date={last ? periodLabel(last.date, series.periodicity) : null} />
    </PaperSheet>
  )
}
