---
id: G-053
title: "Leader"
status: entwurf
typ: begriff
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
verknuepft: [AE-052, QS-010]
---

# G-053: Leader

**Code-Name:** `LeaderElectionService.isLeader()`, Kubernetes-Lease `chronos-scheduler`, Konfiguration `scheduler.leader-election.enabled` (Umgebung `LEADER_ELECTION_ENABLED`)

Der eine Backend-Pod, der die Lease hält und allein die zeitgesteuerten Jobs ([Erinnerungen](G-010-erinnerung.md), Prüfung der [Mindestteilnehmerzahl](G-049-mindestteilnehmerzahl.md)) ausführt ([AE-052](../09-entscheidungen/AE-052-zeitgesteuerte-jobs-mit-leader-election.md)). Ohne Leader-Election (Tests, lokal) ist jeder Pod Leader.
