import { StackedBars } from '../paper/StackedBars'
import { SmallMultiples } from '../paper/SmallMultiples'
import { PaperSheet } from '../paper/PaperSheet'
import type { IndicatorsResponse, Series } from '../types'

const CRIMES: Array<[string, string]> = [
  ['hurto-personas', 'Hurto a personas'],
  ['extorsion', 'Extorsión'],
  ['secuestro', 'Secuestro'],
  ['delitos-informaticos', 'Delitos informáticos'],
  ['violencia-intrafamiliar', 'Violencia intrafamiliar'],
]
const MONTHS = 12

/** Panorama de delitos: barras apiladas de los últimos 12 meses y un mini-gráfico por delito. */
export function Panorama({ data }: { data: IndicatorsResponse }) {
  const all = data.themes.flatMap((t) => t.series)
  const byId = new Map<string, Series>(all.map((s) => [s.id, s]))
  const found = CRIMES.map(([id, label]) => ({ id, label, series: byId.get(id) ?? null }))
  const available = found.filter((f) => f.series && f.series.points.length > 0)
  if (available.length === 0) {
    return (
      <PaperSheet as="section" className="chart-sheet">
        <h3 style={{ marginTop: 0 }}>Panorama de delitos</h3>
        <p className="card-empty">sin datos de delitos por ahora</p>
      </PaperSheet>
    )
  }
  // meses comunes: los últimos 12 hasta el último mes que tenga alguna serie
  const monthOf = (d: string) => d.slice(0, 7)
  const allMonths = Array.from(new Set(available.flatMap((f) => f.series!.points.map((p) => monthOf(p.date))))).sort()
  const months = allMonths.slice(-MONTHS)
  const values = months.map((m) =>
    found.map((f) => {
      const p = f.series?.points.find((q) => monthOf(q.date) === m)
      return p ? p.value : null
    }),
  )
  const missing = found.filter((f) => !f.series || f.series.points.length === 0)
  return (
    <PaperSheet as="section" className="chart-sheet">
      <h3 style={{ marginTop: 0 }}>Panorama de delitos (últimos {months.length} meses)</h3>
      <StackedBars categories={found.map((f) => f.label)} periods={months} values={values}
                   caption="Delitos registrados por la Policía, por mes" unit="casos" />
      <p className="chip">
        Las barras suman los delitos; los de menor volumen (secuestro) casi no se ven, por eso abajo cada delito
        tiene su propia escala. El último mes publicado por la Policía se descarta por llegar incompleto.
      </p>
      {missing.length > 0 && (
        <p className="chip">sin dato: {missing.map((m) => m.label).join(', ')}</p>
      )}
      <SmallMultiples series={available.map((f) => ({ label: f.label, points: f.series!.points.filter((p) => months.includes(monthOf(p.date))) }))} />
    </PaperSheet>
  )
}
