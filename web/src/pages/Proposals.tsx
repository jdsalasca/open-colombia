import { PaperSheet } from '../paper/PaperSheet'
import type { Insight } from '../types'

const LEVEL_COLOR = { alta: 'green', media: 'yellow', baja: 'kraft', insuficiente: 'cream' } as const

export function InsightBlock({ insight }: { insight: Insight }) {
  const { confidence: c } = insight
  return (
    <PaperSheet as="article" color={LEVEL_COLOR[c.level]} className="insight">
      <h3>{insight.title}</h3>
      <p className="label">Qué dicen los datos</p>
      <p>{insight.finding}</p>
      <p className="label">Qué dice la literatura</p>
      <p>{insight.literature}</p>
      <p className="label">Propuesta</p>
      <p>{insight.proposal}</p>
      <p>
        <span className="pill">
          n = {c.n}
          {c.r2 !== null ? ` · R² = ${c.r2.toFixed(2).replace('.', ',')}` : ''} · confianza: {c.level}
        </span>
      </p>
    </PaperSheet>
  )
}

export function Proposals({ insights }: { insights: Insight[] }) {
  return (
    <>
      <h2>Qué podemos hacer</h2>
      <p className="lead">
        Análisis ilustrativo a partir de las series abiertas: tendencias y asociaciones estadísticas simples,
        contrastadas con literatura económica. No es política pública oficial ni prueba causalidad; cada bloque
        declara su n y su R².
      </p>
      {insights.length === 0 ? (
        <PaperSheet>
          <p>Aún no hay suficientes datos para proponer. Pulsa «Actualizar datos».</p>
        </PaperSheet>
      ) : (
        insights.map((i) => <InsightBlock key={`${i.themeId}-${i.title}`} insight={i} />)
      )}
    </>
  )
}
