import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DataTable } from './DataTable'

describe('DataTable', () => {
  it('puts a wide table inside its own horizontal scroller so the page never scrolls sideways', () => {
    const { container } = render(
      <DataTable caption="c" headers={['a', 'b', 'c', 'd', 'e', 'f']} rows={[[1, 2, 3, 4, 5, 6]]} />,
    )
    const scroller = container.querySelector('details > .table-scroll')
    expect(scroller).not.toBeNull()
    expect(scroller?.querySelector('table')).not.toBeNull()
    expect(scroller).toHaveAttribute('tabindex', '0') // desplazable con teclado
  })
})
