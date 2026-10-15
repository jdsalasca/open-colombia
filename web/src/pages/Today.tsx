import { Link } from 'react-router-dom'
import { agoLabel, bogotaDate, bogotaTime, fmt, minutesSince } from '../format'
import { LineChart } from '../paper/LineChart'
import { PaperSheet } from '../paper/PaperSheet'
import type { IndicatorsResponse, LiveSnapshot, Series } from '../types'

const LIVE_WINDOW_MIN = 30

function find(data: IndicatorsResponse, id: string): Series | null {
  for (const t of data.themes) {
    const s = t.series.find((x) => x.id === id)
    if (s && s.points.length > 0) return s
  }
  return null
}

function Card({ label, children, to }: { label: string; children: React.ReactNode; to?: string }) {
  return (
    <PaperSheet as="article" ariaLabel={label} className="card today-card">
      <h3 className="card-title">{label}</h3>
      {children}
      {to && (
        <p className="chip">
          <Link to={to}>¿cómo se calcula?</Link>
        </p>
      )}
    </PaperSheet>
  )
}

function Badge({ children }: { children: React.ReactNode }) {
  return <span className="live-badge">{children}</span>
}

function Dollar({ live, now }: { live: LiveSnapshot | null; now: Date }) {
  const u = live?.usdCop
  if (!u) {
    return (
      <Card label="Dólar hoy (TRM intradía)" to="/metodologia">
        <p className="card-empty">sin dato</p>
        <p className="chip">
          {live?.errors?.banrep ? 'La fuente no respondió.' : 'Sin cotizaciones hoy (mercado cerrado o aún sin abrir).'}
        </p>
      </Card>
    )
  }
  const fresh = minutesSince(u.lastTickAt, now) <= LIVE_WINDOW_MIN
  const up = u.changePct > 0
  return (
    <Card label="Dólar hoy (TRM intradía)" to="/metodologia">
      <p className="card-value" aria-label={`Dólar: ${fmt(u.last)} pesos`}>
        {fmt(u.last)} <span className="card-unit">COP/USD</span>
      </p>
      <p className="card-delta">
        {up ? '▲' : u.changePct < 0 ? '▼' : '■'} {up ? '+' : ''}{fmt(u.changePct)} % vs. apertura ({fmt(u.open)})
      </p>
      <LineChart
        points={u.ticks.map((t) => ({ date: t.t, value: t.v }))}
        unit="COP/USD"
        height={130}
        xLabel={bogotaTime}
      />
      <p className="chip">
        {fresh ? (
          <Badge>en vivo</Badge>
        ) : (
          <>último dato: {bogotaTime(u.lastTickAt)} del {bogotaDate(u.lastTickAt)}</>
        )}{' '}
        · Banco de la República (endpoint público sin contrato oficial)
      </p>
    </Card>
  )
}

function LatestQuake({ live, now }: { live: LiveSnapshot | null; now: Date }) {
  if (!live) {
    return (
      <Card label="Último sismo (M ≥ 2,5)" to="/metodologia">
        <p className="card-empty">sin dato</p>
      </Card>
    )
  }
  const q = live.quakes[0]
  const weekAgo = now.getTime() - 7 * 86400000
  const week = live.quakes.filter((x) => Date.parse(x.time) >= weekAgo).length
  const failed = Boolean(live.errors?.usgs)
  const fresh = !failed && live.fetchedAt !== null && minutesSince(live.fetchedAt, now) <= LIVE_WINDOW_MIN
  return (
    <Card label="Último sismo (M ≥ 2,5)" to="/metodologia">
      {q ? (
        <>
          <p className="card-value">
            M {fmt(q.mag)}
          </p>
          <p className="card-delta">{q.place} · profundidad {fmt(q.depthKm)} km</p>
          <p className="card-delta">
            {bogotaDate(q.time)} {bogotaTime(q.time)} · {week} {week === 1 ? 'sismo' : 'sismos'} M ≥ 2,5 en 7 días
          </p>
        </>
      ) : (
        <p className="card-empty">sin sismos M ≥ 2,5 en 30 días</p>
      )}
      <p className="chip">
        {failed ? (
          <>No se pudo actualizar; se muestra el último dato guardado.</>
        ) : fresh ? (
          <Badge>en vivo</Badge>
        ) : live.fetchedAt ? (
          <>actualizado {bogotaDate(live.fetchedAt)} {bogotaTime(live.fetchedAt)}</>
        ) : (
          <>sin actualizar</>
        )}{' '}
        · USGS
      </p>
    </Card>
  )
}

function DailyCard({ series, label, extra }: { series: Series | null; label: string; extra?: (s: Series) => string }) {
  const to = series ? `/metodologia/${series.id}` : '/metodologia'
  if (!series) {
    return (
      <Card label={label} to={to}>
        <p className="card-empty">sin dato</p>
      </Card>
    )
  }
  const last = series.points.reduce((a, b) => (a.date >= b.date ? a : b))
  const f = series.freshness
  return (
    <Card label={label} to={to}>
      <p className="card-value" aria-label={`${label}: ${fmt(last.value)} ${series.unit}`}>
        {fmt(last.value)} <span className="card-unit">{series.unit}</span>
      </p>
      {extra && <p className="card-delta">{extra(series)}</p>}
      <p className="chip">
        dato del {last.date}
        {f ? ` · ${agoLabel(f.ageDays)}` : ''}
        {f?.stale ? ' · ⚠ desactualizado' : ''} · {series.source}
      </p>
    </Card>
  )
}

/** «Hoy en Colombia»: lo más reciente. Solo el dólar y los sismos pueden rotularse «en vivo». */
export function Today({ live, data, now = new Date() }: { live: LiveSnapshot | null; data: IndicatorsResponse; now?: Date }) {
  const temp = find(data, 'temperatura-nacional')
  const price = find(data, 'energia-precio-bolsa')
  return (
    <section aria-labelledby="hoy-titulo" className="today">
      <h2 id="hoy-titulo">Hoy en Colombia</h2>
      <div className="grid">
        <Dollar live={live} now={now} />
        <LatestQuake live={live} now={now} />
        <DailyCard
          series={temp}
          label="Temperatura nacional (promedio diario)"
          extra={(s) => {
            const last30 = [...s.points].sort((a, b) => a.date.localeCompare(b.date)).slice(-30)
            const avg = last30.reduce((acc, p) => acc + p.value, 0) / last30.length
            return `promedio de los últimos ${last30.length} días: ${fmt(avg)} ${s.unit}`
          }}
        />
        <DailyCard series={price} label="Precio de bolsa de energía (promedio diario)" />
      </div>
    </section>
  )
}
