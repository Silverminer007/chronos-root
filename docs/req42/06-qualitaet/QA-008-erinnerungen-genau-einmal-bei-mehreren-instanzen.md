---
id: QA-008
title: Erinnerungen genau einmal bei mehreren Instanzen
status: entwurf
typ: qualitaetsanforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java, backend/src/test/java/de/chronos_live/chronos_date_api/application/LeaderElectionServiceTest.java]
verknuepft: [FA-011]
---

# QA-008: Erinnerungen genau einmal bei mehreren Instanzen

**Qualitätsmerkmal (ISO 25010):** Zuverlässigkeit (Korrektheit)

## Szenario
Laufen mehrere Backend-Instanzen (Produktion: zwei Replikate, Autoskalierung 1 bis 4), erhält jede Person jede zeitgesteuerte Meldung (Erinnerung, Prüfung der Mindestteilnehmerzahl) genau einmal; fällt die ausführende Instanz aus, übernimmt eine andere innerhalb von etwa 15 Sekunden.

## Messbar durch
Backend-Tests zur Instanzwahl. Der Übernahmezeitraum ist aus der Konfiguration abgeleitet (Lease-Dauer 15 s, Erneuerung nach 10 s), nicht abgestimmt.
