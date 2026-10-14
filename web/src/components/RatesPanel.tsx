import { fmt, periodLabel } from '../format'
import type { Rates } from '../types'

interface Props {
  rates: Rates | null
  unit: string
  periodicity?: string
}

const pct = (v: number | null) => (v === null ? '—' : `${fmt(v)} %`)

/** Tasas de la ventana visible. Con menos de 2 puntos dice «insuficiente», nunca NaN. */
export function RatesPanel({ rates, unit, periodicity = 'mensual' }: Props) {
  if (!rates) {
    return <p className="card-empty">Tasas: datos insuficientes en este rango.</p>
  }
  const when = (d: string) => periodLabel(d, periodicity)
  return (
    <dl className="rates">
      <dt>Variación</dt>
      <dd>
        {rates.change > 0 ? '+' : ''}
        {fmt(rates.change)} {unit} ({pct(rates.changePct)})
      </dd>
      <dt>Anualizada</dt>
      <dd>{pct(rates.annualizedPct)}</dd>
      <dt>Promedio</dt>
      <dd>{fmt(rates.mean)} {unit}</dd>
      <dt>Mínimo</dt>
      <dd>{fmt(rates.min.value)} ({when(rates.min.date)})</dd>
      <dt>Máximo</dt>
      <dd>{fmt(rates.max.value)} ({when(rates.max.date)})</dd>
      <dt>Volatilidad</dt>
      <dd>{fmt(rates.stdDev)} {unit} (desv. est., n = {rates.n})</dd>
    </dl>
  )
}
