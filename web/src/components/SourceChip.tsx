import type { Series } from '../types'

interface Props {
  series: Series
  date: string | null
}

export function SourceChip({ series, date }: Props) {
  return (
    <p className="chip">
      <a href={series.sourceUrl} target="_blank" rel="noreferrer">
        {series.source}
      </a>
      {date ? ` · ${date}` : ''} · {series.periodicity}
      {series.freshness?.stale ? ' · ⚠ desactualizado' : ''}
    </p>
  )
}
