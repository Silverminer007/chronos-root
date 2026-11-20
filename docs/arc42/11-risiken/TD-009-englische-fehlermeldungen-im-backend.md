---
id: TD-009
title: "Englische Fehlermeldungen im Backend"
status: entwurf
typ: technische-schuld
quellen:
  - backend/src/main/java/de/chronos_live/chronos_date_api/application/AuthorizationService.java
  - frontend/app/utils/errors.ts
verknuepft: [RB-001, KZ-011]
---

# TD-009: Englische Fehlermeldungen im Backend

Das Frontend zeigt die `message` der Backend-Antwort an ([KZ-011](../08-konzepte/KZ-011-deutsche-oberflaeche.md)): zuerst die `message` aus `ErrorResponse`, sonst ein deutscher Fallbacktext der Aufrufstelle (`getErrorMessage`). Die fachlichen Meldungen der `ForbiddenException`/`ValidationException` sind englisch („You have to be responsible for this appointment"), der Fehlercode (`errorCode`) wird nicht in deutsche Texte übersetzt. Nutzer:innen sehen dadurch gegebenenfalls englische Texte.

**Auswirkung:** Die Randbedingung [RB-001](../../req42/07-randbedingungen/RB-001-alle-ui-texte-sind-deutsch.md) (alle sichtbaren Texte deutsch) ist verletzt. Deutsch sind nur Systemnachrichten und die generische 500-Meldung; die E2E-Tests prüfen deutsche Texte nur auf Landing- und öffentlichen Seiten, Fehlermeldungen sind nicht getestet.

**Maßnahme:** Fehlercodes im Frontend in deutsche Texte abbilden oder die Backend-Meldungen übersetzen.
