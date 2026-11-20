---
id: LZ-012
title: Start und Stop einer Backend-Instanz
status: entwurf
typ: laufzeitszenario
quellen:
  - backend/src/main/resources/application.properties
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/LeaderElectionService.java
  - deployment/values.yaml
  - deployment/templates/backend-deployment.yaml
verknuepft: [BB-005, BB-012, LS-012, VT-002, KT-005]
---

# Start und Stop einer Backend-Instanz

Betriebsszenario für jede Kubernetes-Instanz (`chronos-backend`, mehrere Replikate, [VT-002](../07-verteilung/VT-002-kubernetes-ressourcen.md)).

Start:
1. Pod startet mit Umgebungsvariablen und Secrets (`DB_*`, `AUTH_SERVER_*`, `VAPID_*`, [VT-003](../07-verteilung/VT-003-secrets-provider.md)).
2. Quarkus verbindet sich mit PostgreSQL und führt Flyway aus (`migrate-at-start`, [KZ-006](../08-konzepte/KZ-006-flyway-migrationen.md)); mehrere gleichzeitig startende Instanzen serialisiert Flyway über die Datenbank. Schlägt eine Migration fehl, startet die Instanz nicht.
3. `WebPushAdapter` prüft die VAPID-Werte; fehlen sie, warnt das Backend und der Push-Versand bleibt aus ([KT-004](../03-kontext/KT-004-push-dienste.md)).
4. `LeaderElectionService` startet beim `StartupEvent` die Bewerbung um das Lease `chronos-scheduler` ([BB-012](../05-bausteine/BB-012-java-reminder.md)).
5. Kubernetes prüft die Liveness (`/q/health/live`, Port 9000, nach 30 s, alle 10 s) und Readiness (`/q/health/ready`, nach 5 s, alle 5 s); erst bei Readiness erhält die Instanz Verkehr vom Service.

Stop (Rolling Update, Skalierung, Absturz):
- Beim `ShutdownEvent` setzt die Instanz `leader=false` und bricht die Wahl ab; `withReleaseOnCancel` gibt das Lease frei, sodass eine andere Instanz die Führung übernimmt. Stürzt der Leader ohne Freigabe ab, läuft das Lease nach 15 s aus ([LZ-006](LZ-006-erinnerung-scheduler.md)).
- Anfragen verteilt der Service auf die verbleibenden Instanzen; das Backend hält keinen Sitzungszustand ([BB-019](../05-bausteine/BB-019-whitebox-gesamtsystem.md)).
- Fällt PostgreSQL aus, scheitern Readiness und Anfragen, bis die Datenbank zurück ist ([R-006](../11-risiken/R-006-datenbank-ist-einziger-zustandsspeicher.md)).
