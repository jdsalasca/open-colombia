import type { CSSProperties, ReactNode } from 'react'

interface Props {
  children: ReactNode
  color?: 'cream' | 'yellow' | 'blue' | 'red' | 'green' | 'kraft'
  tilt?: number
  className?: string
  as?: 'div' | 'section' | 'article'
  /** Nombre accesible (p. ej. para `article`). */
  ariaLabel?: string
}

/** Hoja de papel: color plano, borde levemente irregular, sombras apiladas y leve inclinación. */
export function PaperSheet({ children, color = 'cream', tilt = 0, className = '', as: Tag = 'div', ariaLabel }: Props) {
  const style = { '--tilt': `${tilt}deg` } as CSSProperties
  return (
    <Tag className={`sheet-wrap ${className}`} style={style} aria-label={ariaLabel}>
      <div className={`sheet sheet-${color}`}>{children}</div>
    </Tag>
  )
}
