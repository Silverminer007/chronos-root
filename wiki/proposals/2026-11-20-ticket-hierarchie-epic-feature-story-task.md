---
title: Ticket-Hierarchie Epic, Feature, User-Story, Task
datum: 2026-11-20
betrifft: docs/req42/04-product-backlog/README.md
art: neu
status: angenommen   # Mensch setzt: angenommen | abgelehnt | zurückgestellt
---

## Beobachtung

- req42 kennt im Product Backlog „Epics, Features und User Storys“ als Granularitätsstufen (`docs/req42/04-product-backlog/00-kapitelbeschreibung.md`), das Projekt definiert sie aber nirgends.
- `docs/req42/04-product-backlog/README.md` kennt heute nur Features (`F-nnn`) und Anforderungen (`FA-nnn`, Stories mit Akzeptanzkriterien); „Epics und Stories“ stehen als offene GitHub-Tickets in der Roadmap.
- GitHub kennt das Label `epic` („Übergeordnetes Ticket mit Unterpunkten“, z. B. #381, #298). Umsetzungstickets tragen `enhancement`, `ready-for-agent` und `area:*`; Agenten arbeiten sie je Ticket mit einem PR `Resolve #<Ticket>: <Titel>` / `Closes #<Ticket>` ab (`TS-001`, `RA-001`).
- Die Angular-Tickets #361 bis #379 sind faktisch Tasks (ein Agent, ein PR), heißen aber wie Features; #380 (`needs-po`) ist ein Entscheidungsticket ohne Typ. Ohne Definition fehlen Abgrenzung und einheitliche Fertigkeitskriterien.

## Vorschlag

Neuer Abschnitt „Ticket-Typen“ in `docs/req42/04-product-backlog/README.md` (oder eigene Datei `ticket-typen.md` im selben Ordner).

### 1. Überblick und Abgrenzung

| Typ | Frage, die er beantwortet | Zuschnitt | Abgeschlossen durch | Ablage |
|---|---|---|---|---|
| **Epic** | Welches Ziel erreichen wir? | Mehrere Features, mehrere Wochen bis Monate; entspricht einem Roadmap-Eintrag (`RM-nnn`) | Alle Kind-Tickets erledigt, Ziel überprüft | GitHub-Ticket, Label `epic`; Roadmap `docs/req42/11-roadmaps/` |
| **Feature** | Welche Fähigkeit bekommt der Nutzer? | Zusammenhängender Funktionsumfang, ein Sprint bis wenige Wochen, besteht aus mehreren Stories | Alle Stories erledigt, Feature im Produkt nutzbar | GitHub-Ticket, Label `feature`; nach Umsetzung als `F-nnn` in `docs/req42/04-product-backlog/features/` |
| **User-Story** | Was will wer damit erreichen, und woran erkennen wir das? | Ein Nutzerwert, einzeln auslieferbar und abnehmbar, in einem Sprint fertig; beschreibt das **Was**, nicht das **Wie** | Alle Akzeptanzkriterien nachweislich erfüllt, Produkt-Owner-Abnahme | GitHub-Ticket, Label `story`; nach Umsetzung als `FA-nnn` in `docs/req42/04-product-backlog/anforderungen/` |
| **Task** | Welche technische Arbeit setzt das um? | Ein Arbeitspaket für **einen** Bearbeiter (Mensch oder Agent) und **einen** PR, möglichst unter einem Tag | PR gemergt, Prüfungen grün | GitHub-Ticket, Label `task` (+ `ready-for-agent`, `area:*`) |

Trennlinien:

- **Epic ↔ Feature:** Ein Epic ist an einem Ziel (`Z-nnn`) gemessen, ein Feature an einer Nutzerfähigkeit. Ein Epic kann ohne Neuentwicklung nicht abgeschlossen werden, liefert aber selbst keinen Code.
- **Feature ↔ Story:** Ein Feature wird erst durch das Zusammenspiel mehrerer Stories sichtbar; eine Story ist allein abnehmbar. Lässt sich ein Feature in einem Stück abnehmen, ist es eine Story.
- **Story ↔ Task:** Die Story hat einen Nutzer und eine Abnahme, der Task hat nur einen Bearbeiter und einen PR. Der Nutzer kennt Tasks nicht. Fachlich prüfbar = Story, technisch prüfbar = Task.
- **Task ↔ Bug:** Fehler bleiben Label `bug`; sie sind weder Story noch Task und brauchen keine Akzeptanzkriterien, sondern Reproduktion und erwartetes Verhalten.
- **Entscheidungstickets** (`needs-po`, wie #380) sind keine eigenen Typen; sie hängen an einem Epic oder Feature und blockieren dessen Kinder, bis der Product Owner entschieden hat.
- **Technische Arbeit ohne Nutzerwert** (Refactoring, CI, Deployment) ist ein Task direkt unter einem Feature oder Epic, keine Story mit erfundenem Nutzer.

Hierarchie: Epic 1 : n Feature 1 : n Story 1 : n Task. Ein Ticket hat genau ein Elternticket; die Verknüpfung steht im Ticketkopf („Teil von #…“) und über GitHub-Sub-Issues bzw. Task-Liste im Elternticket.

### 2. Struktur je Typ

Alle Tickets: deutscher Titel, ein Typ-Label, Bereichs-Label (`area:java` | `area:frontend` | `area:deployment` | `area:docs`), Verweis auf Elternticket.

**Epic**

```markdown
## Ziel
Welches Ziel (Z-nnn) und welchen Mehrwert bringt das Epic? Für wen (SH-nnn)?

## Erfolgskriterien
Messbare Aussagen, an denen sich das Erreichen zeigt (nicht Aufgabenliste).

## Abgrenzung
Was gehört ausdrücklich nicht dazu?

## Features
- [ ] #…  (Task-Liste, Reihenfolge = Rang)

## Offene Entscheidungen / Risiken
Verweise auf `needs-po`-Tickets, RA-nnn.
```

**Feature**

```markdown
## Als … möchte ich … damit …
Eine Zeile Nutzernutzen auf Fähigkeitsebene.

## Umfang
Was kann der Nutzer danach, was er vorher nicht konnte? Nicht-Ziele.

## Stories
- [ ] #…

## Abhängigkeiten
Andere Features, Dienste (Keycloak, Push …), Betriebsfolgen.
```

**User-Story**

```markdown
## Story
Als <Rolle/Stakeholder> möchte ich <Fähigkeit>, damit <Nutzen>.

## Akzeptanzkriterien
Prüfbare Aussagen, getrennt nach Verhalten und Oberfläche (Gegeben/Wenn/Dann oder Aufzählung), inkl. Fehlerfälle, Berechtigungen, leere/Lade-/Fehlerzustände.

## Nicht Teil dieser Story
Abgrenzung, Folge-Stories.

## Tasks
- [ ] #…

## Hinweise
Betroffene Anforderungen/Modelle (FA-, M-nnn), Design (Figma-Link), offene Fragen.
```

**Task**

```markdown
## Ziel
Ein Satz: Was ist nach dem PR anders?

## Umsetzung
Betroffene Module/Dateien, vorgesehener Ansatz, Randbedingungen (RA-nnn). Genug, dass ein Agent ohne Rückfrage starten kann.

## Fertig, wenn
Prüfbare Liste (Verhalten, Tests, Prüfbefehle).

## Nicht Teil dieses Tasks
Abgrenzung.

## Prüfung
Befehle je Bereich (Java: `./mvnw -B verify`, Frontend: `npm run lint && npm run typecheck && npm run build`).
```

Ein Task trägt `ready-for-agent` erst, wenn Ziel, Umsetzung und „Fertig, wenn“ ausgefüllt sind und keine Rückfrage (`needs-input`, `needs-po`) offen ist.

### 3. Definition of Done

Gilt jeweils **zusätzlich** zu den Kriterien der darunterliegenden Ebene (bottom-up).

**Task – DoD**
- Umsetzung entspricht „Fertig, wenn“ und dem Ziel; nichts außerhalb des Umfangs geändert.
- Tests geschrieben bzw. angepasst (testgetrieben, RA-005); Qualitäts-Gates des Bereichs grün (RA-002, RA-004).
- PR `Resolve #<Ticket>: <Titel>`, Body beginnt mit `Closes #<Ticket>`, gegen `main`/`develop`; CI grün.
- Review erfolgt, Konflikte gelöst, PR gemergt (Mensch, RB-005).
- Keine Geheimnisse im Diff; Migrationen nur vorwärts (RA-012); Oberflächentexte deutsch.
- `docs/` nicht verändert; Erkenntnisse im Wiki festgehalten (`wiki/log.md`), Widersprüche als Vorschlag in `wiki/proposals/`.

**User-Story – DoD**
- Alle Tasks erledigt (Task-DoD).
- Jedes Akzeptanzkriterium ist nachweislich erfüllt: automatisierter Test oder dokumentierte manuelle Prüfung, inklusive Fehler-, Berechtigungs- und Leerzustände.
- Die Story läuft zusammen mit dem Rest des Produkts (Integration auf `main`, kein Feature-Flag-Rest ohne Plan).
- Product Owner hat abgenommen (Ticket kommentiert/geschlossen).
- Anforderung `FA-nnn` ist als Vorschlag in `wiki/proposals/` gemeldet, damit der Ist-Zustand in `docs/req42/04-product-backlog/anforderungen/` ergänzt werden kann.

**Feature – DoD**
- Alle Stories erledigt (Story-DoD).
- Der Funktionsumfang ist Ende-zu-Ende aus Nutzersicht verwendbar (Frontend + Backend + ggf. Push/Keycloak), auf Mobil und Desktop.
- Nichtfunktionale Anforderungen berührter Qualitätsziele geprüft (Barrierefreiheit, Datenschutz, Leistung, Benachrichtigungen), Betrieb berücksichtigt (Helm-Werte, Migrationen, Monitoring).
- In `staging`/Testumgebung verifiziert; Rollback-Pfad bekannt.
- `F-nnn` samt Verknüpfungen (Z-, SH-, FA-) als Vorschlag gemeldet; Nutzer-Doku/„Neuigkeiten“ geklärt.

**Epic – DoD**
- Alle Features erledigt oder bewusst gestrichen (jeweils begründet im Epic).
- Erfolgskriterien geprüft und Ergebnis im Epic festgehalten; Ziel (Z-nnn) erreicht oder Abweichung benannt.
- Produktiv ausgerollt (RB-010), Bestandsdaten migriert, Altlasten entfernt.
- Roadmap-Eintrag (`RM-nnn`) aktualisiert, offene `needs-po`/`follow-up`-Tickets abgearbeitet oder verschoben, Rückblick (was gelernt?) im Wiki.
- Product Owner schließt das Epic.

**Definition of Ready (Kurzfassung)**
- Story: Rolle, Nutzen, prüfbare Akzeptanzkriterien, keine offene `needs-po`-Frage, passt in einen Sprint.
- Task: Ziel, Umsetzung, „Fertig, wenn“, Bereich; Eltern-Story ist bereit; Agent kann ohne Rückfrage starten.

### 4. Beispiel (Terminfindung, RM-006 / #298)

- **Epic** #298 Terminfindung (Ziel: Gruppen finden ohne Chat einen Termin).
- **Feature** „Zeitvorschläge abstimmen“; **Feature** „Laufende Terminanfragen“.
- **Story** „Als Organisator möchte ich mehrere Zeitpunkte vorschlagen, damit die Gruppe abstimmen kann.“
- **Tasks** Datenmodell + Migration (`area:java`), REST-Endpunkt (`area:java`), Abstimmungs-Dialog (`area:frontend`).

### 5. Umstellung

- Neue Labels `feature`, `story`, `task` anlegen (`epic` besteht); `enhancement` bleibt für die Art der Änderung.
- Bestehende Tickets #361 bis #379 sind als Tasks (Angular-Epic #381) umzuwidmen oder zu Stories zusammenzufassen; Entscheidung beim Product Owner.
- Ticket-Vorlagen unter `.github/ISSUE_TEMPLATE/` (je Typ) und `AGENTS.md` verweisen auf diese Definition.
- Reihenfolge/Rang legt weiter der Product Owner fest (RA-013).

## Wirkung

- `docs/req42/04-product-backlog/README.md` bekommt den Abschnitt „Ticket-Typen“; `docs/req42/10-teamstruktur/TS-001-rollen-und-zusammenarbeit.md` nennt den Task als Arbeitseinheit der Agenten.
- Steward und Orchestrator prüfen die Definition of Ready, bevor `ready-for-agent` vergeben wird.
- Agenten (Wiki-Skill) legen `wiki/tickets/` je abgeschlossener Story/Feature an.
- Offen für den Menschen: Ist die Ebene „Feature“ als GitHub-Ticket nötig, oder reicht `F-nnn` in `docs/` (dann Epic → Story → Task)? Sollen Sprintgrößen so gelten?

## Entscheidung (Mensch)
Angenommen am 2026-11-20 (Product Owner, Sitzung). Umgesetzt in `docs/req42/04-product-backlog/ticket-typen.md`, `.github/ISSUE_TEMPLATE/`, GitHub-Labels. Merge von `docs/` bleibt beim Menschen.
