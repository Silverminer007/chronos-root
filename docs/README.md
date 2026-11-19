# docs/ – gemeinsam gepflegte Wahrheit

Dieser Bereich enthält die **verbindlichen** Dokumente zu Chronos. Er gehört Menschen und Agenten gemeinsam, aber
**jede Änderung braucht eine menschliche Freigabe** (Review durch die Code-Owner, siehe `.github/CODEOWNERS`).

| Dokument | Frage, die es beantwortet |
|---|---|
| [vision/product-vision.md](vision/product-vision.md) | Wofür gibt es Chronos, für wen, woran messen wir Erfolg? |
| [req42/](req42/README.md) | Was muss das System leisten (Ziele, Stakeholder, Anforderungen, Qualitätsziele)? |
| [arc42/](arc42/README.md) | Wie ist das System gebaut und warum (Kontext, Bausteine, Laufzeit, Entscheidungen)? |

## Regeln

1. **Menschliche Freigabe.** PRs, die `docs/` ändern, werden nur von einem Menschen gemergt. Agenten dürfen Änderungen
   *vorschlagen* (PR oder Datei in `wiki/proposals/`), aber nie selbst freigeben oder automatisch mergen.
2. **Nur Verbindliches.** Was hier steht, gilt. Beobachtungen, Vermutungen und Gelerntes stehen im [wiki/](../wiki/README.md).
3. **Änderungen sind Entscheidungen.** Jeder PR nennt Anlass (Ticket/Gespräch) und Wirkung. Ändert sich eine
   Architekturentscheidung, wird sie in `arc42/09-entscheidungen.md` ersetzt, nicht stillschweigend überschrieben.
4. **Sprache.** Deutsch, wie die Oberfläche. Code-Namen bleiben englisch (Zuordnung: Glossar).
5. **Status im Frontmatter.** `status: entwurf | freigegeben`, `freigegeben-am`, `freigegeben-von`. Entwürfe gelten noch nicht.
6. **Eine Datei, ein Thema.** Kurz halten; Details gehören in Code, Tests oder das Wiki, nicht hierher.

## Wie ein Agent hier arbeitet

Agenten lesen `docs/` als Eingabe, schreiben aber nur nach `wiki/`. Findet ein Agent einen Widerspruch zwischen
`docs/` und Code oder Tickets, legt er `wiki/proposals/<datum>-<thema>.md` an (Vorlage dort). Ein Mensch entscheidet,
ob daraus ein PR gegen `docs/` wird.
