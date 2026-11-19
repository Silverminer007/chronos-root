# wiki/ – LLM-Wiki für Agenten

Dieser Bereich gehört den **Agenten**. Sie bauen hier aus Dokumenten, Tickets, Sessions und Code eine Wissensbasis auf, die sie beim nächsten Mal nutzen können, damit nicht jede Session bei null beginnt. Menschen dürfen jederzeit lesen und korrigieren, müssen aber nicht freigeben.

Das Wiki ist **nicht verbindlich**. Was gilt, steht in [docs/](../docs/README.md). Widerspricht das Wiki `docs/`, gewinnt `docs/`.

## Drei Schichten

| Schicht | Ort | Wer schreibt | Zweck |
|---|---|---|---|
| Quellen | `docs/`, GitHub-Tickets/PRs, Session-Verläufe, Code | Menschen / Entwicklung | Wahrheit, nur lesend |
| Wiki | `wiki/` | Agenten | Verdichtetes, verknüpftes Wissen |
| Regeln | dieses README, `.claude/skills/chronos-wiki/` | gemeinsam (Review) | Wie Agenten das Wiki pflegen |

## Aufbau

| Pfad | Inhalt |
|---|---|
| `index.md` | Katalog aller Seiten mit je einer Zeile. Agenten lesen **zuerst** diese Datei. |
| `log.md` | Chronologisch, nur anhängen: Was wurde wann aus welcher Quelle gelernt? |
| `concepts/` | Fachliche und technische Begriffe, Domänenmodell, Glossar |
| `code/` | Landkarte des Codes: Modul → Zweck, Einstiegspunkte, Stolperstellen (Java, Rust, Frontend) |
| `decisions/` | Detail-Entscheidungen und Begründungen (Herkunft: bisherige `kb/adr/`) |
| `lessons/` | Aus Sessions und Reviews gelernt: Fehler, Workarounds, Kommandos, die funktionieren |
| `tickets/` | Verdichtung von Ticket-Verläufen, wenn sie später noch helfen |
| `status/` | Berichte des Stewards (bisher `kb/status/`) |
| `proposals/` | Vorschläge für Änderungen an `docs/`; Ausgang zum Menschen |

## Seitenformat

```markdown
---
title: Kurzer Titel
type: concept | code | decision | lesson | ticket
sources:                # woher stammt das? mindestens eine Angabe
  - docs/arc42/03-kontext.md
  - rust-backend/src/events/mod.rs@0e7a609
  - issue:#346
confidence: hoch | mittel | niedrig
last-verified: 2026-11-19   # Datum, an dem zuletzt gegen die Quelle geprüft wurde
supersedes:             # optional
---
```

Regeln für Inhalte: kurz, verlinkt (`[[concepts/appointment]]`-artig als relative Markdown-Links), **jede Aussage hat eine Quelle**, und Code-Aussagen nennen Datei und Commit, weil Code sich ändert.

## Arbeitsabläufe

1. **Ingest.** Neue Quelle (gemergtes Ticket, Session, Code-Änderung): relevante Seiten aktualisieren oder anlegen, `index.md` ergänzen, einen Eintrag in `log.md` anhängen.
2. **Query.** Vor einer Aufgabe `index.md` lesen, passende Seiten laden, **gegen den Code prüfen**, wenn die Aufgabe davon abhängt (`last-verified` ist alt oder `confidence` niedrig). Hat die Antwort bleibenden Wert, als Seite zurückschreiben.
3. **Lint.** Regelmäßig (Steward/Nightly): tote Links, Seiten ohne `sources`, veraltetes `last-verified`, Widersprüche zu `docs/`, verwaiste Seiten. Ergebnis in `status/`.
4. **Promote.** Ein Agent hält etwas für verbindlich (Anforderung, Architekturänderung, Widerspruch zu `docs/`)? Er schreibt einen Vorschlag nach `proposals/` ([Vorlage](proposals/_vorlage.md)) und ändert `docs/` **nicht**.

## Grenzen

- Keine Geheimnisse, Tokens, personenbezogenen Daten oder Session-Rohverläufe ins Wiki.
- Keine Kopien von Code oder `docs/`; verlinken und zusammenfassen.
- Löschen nur mit Eintrag in `log.md`; veraltete Seiten erhalten `status: veraltet` statt stillem Verschwinden.
