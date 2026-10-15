import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMethodologyIndex } from '../api'
import { PaperSheet } from '../paper/PaperSheet'
import type { Methodology } from '../types'

const GLOSSARY: Array<[string, string]> = [
  ['Variación', 'Último valor de la ventana menos el primero. En % se divide por el valor inicial.'],
  ['Anualizada', 'Tasa que, sostenida un año, llevaría del valor inicial al final: ((final/inicial)^(365,25/días) − 1) × 100. Solo si la ventana abarca cerca de un año o más y el valor inicial es positivo.'],
  ['Promedio y volatilidad', 'Media aritmética y desviación estándar muestral de los datos de la ventana.'],
  ['Tendencia y R²', 'Recta de mínimos cuadrados (MCO) del valor contra el tiempo; R² dice qué parte de la variación explica la recta. Con menos de 5 datos no se estima.'],
  ['Variación interanual', 'Cambio de un año contra el anterior (solo años consecutivos). En series que ya son porcentajes se expresa en puntos porcentuales (pp).'],
  ['Intervalo de predicción', 'Rango (95 %) en el que, bajo el modelo lineal, caería el próximo dato. En series autocorrelacionadas suele ser más estrecho de lo que debería: úsalo como orden de magnitud.'],
  ['Correlación de variaciones', 'Correlación entre los cambios anuales de dos series. Distingue co-movimiento real de dos series que solo comparten tendencia (relación espuria).'],
  ['Series que ya son porcentajes', 'La anualizada y el cambio relativo no se muestran para series que ya son porcentajes (inflación, desempleo, PIB): el cambio se da en puntos porcentuales.'],
]

export function MethodologyIndex() {
  const [items, setItems] = useState<Methodology[] | null>(null)

  useEffect(() => {
    let live = true
    getMethodologyIndex()
      .then((r) => live && setItems(r))
      .catch(() => live && setItems([]))
    return () => {
      live = false
    }
  }, [])

  return (
    <>
      <h2>Metodología</h2>
      <p className="lead">
        Cada cifra sale de una consulta pública que puedes repetir. Aquí está cómo leer las tasas y de dónde
        viene cada indicador.
      </p>
      <PaperSheet color="kraft">
        <h3 style={{ marginTop: 0 }}>Cómo leer las tasas</h3>
        <dl className="rates">
          {GLOSSARY.map(([t, d]) => (
            <div key={t} style={{ display: 'contents' }}>
              <dt>{t}</dt>
              <dd>{d}</dd>
            </div>
          ))}
        </dl>
      </PaperSheet>
      <h3>Indicadores</h3>
      {items === null && <p>Cargando…</p>}
      <ul className="links">
        {(items ?? []).map((m) => (
          <li key={m.id}>
            <Link to={`/metodologia/${m.id}`}>{m.name}</Link> — {m.source}, {m.periodicity}
          </li>
        ))}
      </ul>
    </>
  )
}
