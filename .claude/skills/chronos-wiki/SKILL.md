---
name: chronos-wiki
description: Pflegt und nutzt das LLM-Wiki in wiki/ (Ingest, Query, Lint, Promote). Verwenden, wenn Wissen aus Tickets, Sessions, Code oder docs/ gelernt, nachgeschlagen oder als Vorschlag für docs/ gemeldet werden soll.
---

# chronos-wiki

Regeln und Seitenformat stehen in `wiki/README.md`; **dort zuerst lesen**.

1. **Vor der Arbeit:** `wiki/index.md` lesen, passende Seiten laden. Hängt die Aufgabe von Code ab und ist `last-verified` alt, gegen den Code prüfen.
2. **Nach der Arbeit:** Gelerntes verdichten (Seite anlegen/ändern, `sources` und `last-verified` setzen), `wiki/index.md` und `wiki/log.md` ergänzen.
3. **`docs/` ist tabu.** Dort nie schreiben. Widerspruch oder neue verbindliche Erkenntnis: `wiki/proposals/_vorlage.md` kopieren und ausfüllen.
4. Keine Geheimnisse, Tokens, personenbezogenen Daten oder Rohverläufe von Sessions ins Wiki.
