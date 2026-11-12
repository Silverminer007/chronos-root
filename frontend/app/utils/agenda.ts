/**
 * Fügt eine Antwortseite der Agenda zusammen. Seite 0 (oder keine Seite) ersetzt die
 * Liste, weitere Seiten werden angehängt. Einträge mit bereits vorhandener id werden
 * verworfen; die Reihenfolge bleibt erhalten.
 */
export function mergeAgendaPage<T extends { id: number }>(current: T[], items: T[], page: number | undefined): T[] {
    const base = page === 0 || page == null ? [] : current
    const seen = new Set<number>(base.map(item => item.id))
    const merged = [...base]
    for (const item of items) {
        if (seen.has(item.id)) continue
        seen.add(item.id)
        merged.push(item)
    }
    return merged
}
