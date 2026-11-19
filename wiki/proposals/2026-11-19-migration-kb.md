---
title: Migration von kb/ in docs/ und wiki/
datum: 2026-11-19
betrifft: kb/, docs/, wiki/
art: änderung
status: offen
---

## Beobachtung

`kb/` mischt Verbindliches (Randbedingungen, Grundsatz-ADRs) mit Agenten-Notizen (Parity-Läufe, Statusberichte) und ist als „unveränderlich“ beschrieben, obwohl Agenten es laufend ergänzen. `LLM_WIKI.md` im Wurzelverzeichnis ist ein handgeschriebenes Mental-Model und wird nicht gepflegt. Der Orchestrator behandelt `kb/` als sicheren Pfad (`SAFE_PATHS_RE`), also ohne menschliche Prüfung.

## Vorschlag: Zuordnung

| Bisher | Neu | Bemerkung |
|---|---|---|
| `kb/constraints/C*` | `docs/req42/04-randbedingungen.md` | Mensch bestätigt; C2026-10-20-002 (geleakter Key) → Risiko in `docs/arc42/11` |
| `kb/adr/ADR-2025-09-28-*` (Grundsatz) | `docs/arc42/09-entscheidungen.md` | Mensch wählt, welche gelten |
| übrige `kb/adr/*` (Rust-Port-Details) | `wiki/decisions/` | Agenten dürfen weiterschreiben |
| `kb/architecture/*` | Struktur → `docs/arc42/03–07`, Läufe/Belege → `wiki/lessons/` | |
| `kb/patterns/*` | `wiki/code/` (Agenten), bewährte → `docs/arc42/08` | |
| `kb/glossary/*` | `wiki/concepts/` (Ausnahme: arc42-Kapitel 12 nur auf Wunsch) | |
| `kb/status/*` | `wiki/status/` | Steward schreibt hierhin; `STATUS_DIR` ändern |
| `LLM_WIKI.md` | als Seeds nach `wiki/concepts/` und `wiki/code/`, danach löschen | je Aussage gegen Code prüfen |
| `kb/README.md` | ersetzt durch `docs/README.md` und `wiki/README.md` | |

## Schritte

1. Diesen Vorschlag mergen (nur Gerüst, `kb/` bleibt unverändert).
2. Mensch füllt Vision, req42 und die `(?)`-Stellen und gibt frei.
3. Folge-PRs je Verzeichnis verschieben (`git mv`, damit die Historie bleibt), Links reparieren.
4. Orchestrator umstellen: `KB_DIR=wiki`, `STATUS_DIR=wiki/status`, `STEWARD_DOC`, `AREA_PATHS` (`area:docs=docs/ wiki/`), Prompts `{{KB_DIR}}`.
5. `kb/` entfernen, wenn leer.

## Wirkung

Der Steward und andere Agenten schreiben künftig nur noch nach `wiki/`. Der Skill `chronos-wiki` (noch leer) beschreibt Ingest/Query/Lint nach `wiki/README.md`.

## Entscheidung (Mensch)
