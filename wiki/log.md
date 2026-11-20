# Wiki-Log

Nur anhängen. Format: `## [YYYY-MM-DD] <ingest|query|lint|promote> | <Quelle>` und darunter ein bis drei Zeilen.

## [2026-11-19] ingest | Aufbau der Struktur
Gerüst von `docs/` und `wiki/` angelegt (Vorschlag, noch nicht gemergt).

## [2026-11-20] ingest | Session: Wiki-Integration für Claude Code und opencode
Lektion `lessons/agent-integration-wiki-erzwingen.md` angelegt (Hooks, Plugin, Stolperstellen, versehentlich ersetzte CLAUDE.md, fremde Staged Deletions).

## [2026-11-20] ingest | Stop-Hook-Prüfung korrigiert
`scripts/wiki-stop-check.sh` erkennt gepflegtes Log jetzt per Änderungszeit statt nur per `git status`; Lektion angepasst.

## [2026-11-20] ingest | req42/arc42-Rückwärtsextraktion
Kapitelbeschreibungen (offizielle Vorlagen) in alle Kapitelordner gelegt, req42 auf 12 Artefakte umgebaut, Rust-Backend aus docs/ ausgeklammert (Nutzerentscheidung). Lektion `lessons/req42-arc42-rueckwaerts-extraktion.md`.

## [2026-11-20] ingest | Rückwärtsextraktion abgeschlossen
req42 (12 Artefakte) und arc42 (12 Kapitel) aus dem Code gefüllt, Rust entfernt, Querverweise geprüft. Befunde: `status/befunde-rueckwaertsextraktion-2026-11-20.md`.

## [2026-11-20] ingest | Vorschlag Ticket-Hierarchie
Vorschlag `proposals/2026-11-20-ticket-hierarchie-epic-feature-story-task.md`: Epic/Feature/Story/Task mit Abgrenzung, Ticketstruktur, DoD. Quellen: req42-04, TS-001, RA-001, GitHub-Labels. `docs/` unverändert.

## [2026-11-20] ingest | Qualitätsprüfung der Extraktion
Drei unabhängige Prüfer je Kapitelgruppe: Redundanz, Vollständigkeit gegen Kapitelbeschreibung, Stichproben gegen Code; offene Menschen-Entscheidungen in req42 `12-risiken-annahmen/RA-013` gebündelt, Befunde als TD-010…017 in arc42 Kap. 11.

## [2026-11-20] ingest | Ticket-Typen umgesetzt
Vorschlag `proposals/2026-11-20-ticket-hierarchie-epic-feature-story-task.md` vom Product Owner angenommen und umgesetzt: `docs/req42/04-product-backlog/ticket-typen.md` (Entwurf, Freigabe offen), Issue-Vorlagen `.github/ISSUE_TEMPLATE/`, Labels `feature`/`story`/`task`, Abschnitt in `AGENTS.md`. Beim Übertragen waren RA-002/005/012 im Vorschlag veraltet (arc42-Randbedingungen entfallen), ersetzt durch RB-001/005/008/010/011: Referenz-IDs vor dem Übernehmen in `docs/` gegen vorhandene Dateien prüfen. Bestehende Tickets #361-#379 noch nicht umgelabelt (Entscheidung offen).
