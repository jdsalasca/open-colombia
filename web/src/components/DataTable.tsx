interface Props {
  caption: string
  headers: string[]
  rows: Array<Array<string | number>>
}

/** Tabla de datos accesible, plegada bajo «Ver datos», para que cada gráfica tenga alternativa en texto. */
export function DataTable({ caption, headers, rows }: Props) {
  return (
    <details className="datatable">
      <summary>Ver datos</summary>
      <div className="table-scroll" tabIndex={0} role="region" aria-label={`Tabla: ${caption}`}>
      <table>
        <caption>{caption}</caption>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h} scope="col">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </details>
  )
}
