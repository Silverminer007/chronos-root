import { test, expect } from '@playwright/test'
import { mergeAgendaPage } from '../app/utils/agenda'

const items = (...ids: number[]) => ids.map(id => ({ id }))

test.describe('mergeAgendaPage', () => {
  test('Seite 0 ersetzt die Liste, zweimaliges Laden ergibt N Einträge', () => {
    const first = mergeAgendaPage([], items(1, 2, 3), 0)
    const second = mergeAgendaPage(first, items(1, 2, 3), 0)
    expect(second.map(a => a.id)).toEqual([1, 2, 3])
  })

  test('fehlende Seite wird wie Seite 0 behandelt', () => {
    expect(mergeAgendaPage(items(9), items(1, 2), undefined).map(a => a.id)).toEqual([1, 2])
  })

  test('Seite 1 mit überlappenden ids erzeugt keine Duplikate, Reihenfolge bleibt', () => {
    const merged = mergeAgendaPage(items(1, 2, 3), items(3, 4, 4, 5), 1)
    expect(merged.map(a => a.id)).toEqual([1, 2, 3, 4, 5])
  })

  test('Duplikate innerhalb einer Antwort werden entfernt', () => {
    expect(mergeAgendaPage([], items(1, 1, 2), 0).map(a => a.id)).toEqual([1, 2])
  })
})
