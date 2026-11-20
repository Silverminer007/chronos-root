---
id: R-009
title: "Profil-Upsert bei jeder Anfrage"
status: entwurf
typ: risiko
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/security/PrincipalContextFilter.java
  - backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/LocalDbIdentityAdapter.java
verknuepft: [QA-005, AE-049]
---

# R-009: Profil-Upsert bei jeder Anfrage

**Risiko:** `PrincipalContextFilter` schreibt bei jeder authentifizierten Anfrage per Upsert in `user_profiles` (eigene Transaktion, `REQUIRES_NEW`), auch bei reinen Leseanfragen. Das erzeugt pro Anfrage mindestens eine zusätzliche Datenbanktransaktion.

**Auswirkung:** Höhere Latenz und Datenbanklast bei steigender Nutzung. Gemessen wurde das nicht **(?)** ([QS-009](../10-qualitaet/QS-009-lastverhalten.md)).

**Maßnahmen:** Schreiben nur bei geänderten Claims oder seltener (z. B. beim ersten Aufruf je Token).
