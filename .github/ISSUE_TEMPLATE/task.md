---
name: Task
about: Technisches Arbeitspaket für einen Bearbeiter und einen PR
title: ""
labels: task
---

Teil von #

## Ziel
Ein Satz: Was ist nach dem PR anders?

## Umsetzung
Betroffene Module/Dateien, Ansatz, Randbedingungen. Genug, dass ein Agent ohne Rückfrage starten kann.

## Fertig, wenn
- [ ] 

## Nicht Teil dieses Tasks

## Prüfung
Java: `cd backend && ./mvnw -B verify`; Frontend: `cd frontend && npm run lint && npm run typecheck && npm run build`.

<!-- Label `ready-for-agent` und `area:*` erst setzen, wenn Ziel, Umsetzung und „Fertig, wenn“ ausgefüllt sind und keine Rückfrage offen ist. -->
