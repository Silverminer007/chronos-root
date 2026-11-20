---
id: LS-012
title: Leader-Election für zeitgesteuerte Aufgaben
status: entwurf
typ: strategie
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
  - deployment/templates/backend-role.yaml
verknuepft: [BB-012, LZ-006, QA-008]
---

# Leader-Election für zeitgesteuerte Aufgaben

Das Backend läuft mit mehreren Instanzen (`replicaCount: 2`, optional Autoscaling). Damit zeitgesteuerte Aufgaben (Erinnerungen, Mindestteilnehmer-Prüfung, Bestätigungsereignisse) je Lauf nur einmal ausgeführt werden, führt jede Instanz sie nur als Leader aus. Der Leader wird über ein Kubernetes-Lease bestimmt; das Chart gewährt dem ServiceAccount dafür Rechte (`backend-role.yaml`). `LEADER_ELECTION_ENABLED=false` macht jede Instanz zum Leader (lokal und in Tests). Mechanik: [BB-012](../05-bausteine/BB-012-java-reminder.md), Ablauf: [LZ-006](../06-laufzeit/LZ-006-erinnerung-scheduler.md).
