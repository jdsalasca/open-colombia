import { Link } from 'react-router-dom'
import { PaperCard } from '../paper/PaperCard'
import { PaperSheet } from '../paper/PaperSheet'
import type { IndicatorsResponse, LiveSnapshot } from '../types'
import { Today } from './Today'

const COLORS = ['yellow', 'blue', 'red', 'green', 'kraft', 'cream'] as const

export function Home({ data, live = null }: { data: IndicatorsResponse; live?: LiveSnapshot | null }) {
  return (
    <>
      <Today live={live} data={data} />
      <p className="lead">
        Cómo va Colombia, con datos abiertos oficiales: cada cifra trae su fuente y su fecha. Si una fuente no
        responde, verás «sin dato», nunca un número inventado.
      </p>
      {data.themes.map((t, i) => {
        const withData = t.series.filter((s) => s.points.length > 0)
        return (
          <section key={t.id} aria-labelledby={`h-${t.id}`}>
            <PaperSheet color={COLORS[i % COLORS.length]} tilt={i % 2 === 0 ? -0.4 : 0.4}>
              <h2 id={`h-${t.id}`} style={{ margin: 0 }}>
                <Link to={`/tema/${t.id}`}>{t.name}</Link>
              </h2>
              <p style={{ margin: '4px 0 0' }}>
                {withData.length} de {t.series.length} indicadores con datos · <Link to={`/tema/${t.id}`}>ver tema →</Link>
              </p>
            </PaperSheet>
            <div className="grid">
              {t.series.slice(0, 3).map((s, j) => (
                <PaperCard key={s.id} series={s} tilt={(j % 3) - 1 === 0 ? 0 : (j % 3) - 1 > 0 ? 0.5 : -0.5} />
              ))}
            </div>
          </section>
        )
      })}
    </>
  )
}
