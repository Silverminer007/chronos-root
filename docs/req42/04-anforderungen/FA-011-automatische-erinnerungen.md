---
id: FA-011
title: Automatische Erinnerungen
status: entwurf
typ: anforderung
quellen: [backend/.../application/reminder/]
verknuepft: [F-011]
---

# FA-011: Automatische Erinnerungen

Das System erinnert automatisch an Termine und offene Rückmeldungen.

## Akzeptanzkriterien
- 30 Minuten vor Beginn erfolgt eine Terminerinnerung.
- Termine unter 24 Stunden Dauer erhalten gestaffelte Rückmelde-Erinnerungen (Werktag: 7, 4, 2 und 1 Tag vorher); Wochenende **(?)** Zeitpunkte prüfen.
- Termine ab 24 Stunden erhalten Erinnerungen 16, 8, 4 und 2 Wochen vorher, danach täglich.
- Bei mehreren Instanzen läuft der Scheduler nur auf dem Leader.

## Features
- F-011

## Priorität
**(?)** vom Product Owner festzulegen.
