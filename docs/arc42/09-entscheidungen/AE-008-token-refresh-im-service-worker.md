---
id: AE-008
title: "Token-Refresh im Service Worker"
status: entwurf
typ: architekturentscheidung
datum: 2025-09-28
quellen:
  - frontend/public/push-sw.js
  - frontend/server/api/auth/refresh.post.ts
  - frontend/app/plugins/register-sw.client.ts
  - kb/adr/ADR-2025-09-28-005-service-worker.md (Git-Historie, Begründung und Alternativen)
verknuepft: [AE-003, AE-013, QS-006, R-003]
---

# AE-008: Token-Refresh im Service Worker

**Problem:** Die Nuxt-Middleware (`auth.global.ts`) läuft nur bei Seitenwechseln, nicht bei jedem API-Aufruf; das kurzlebige Access-Token muss vor dem Ablauf erneuert werden, ohne dass Nutzer:innen einen Fehler sehen.

**Entscheidung:** Der Service Worker `push-sw.js` (derselbe wie für Web-Push, [AE-053](AE-053-web-push-direkt-per-vapid.md)) fängt alle `/api/*`-Anfragen ab:
- Liest das Ablaufdatum aus dem lesbaren Cookie `kc_expires`; liegt es unter 5 s, ruft er `POST /api/auth/refresh` auf, vorher schon per Timer 30 s vor Ablauf.
- Höchstens ein Refresh gleichzeitig (`inflightRefresh`); bei 401 wiederholt er die Anfrage einmal nach Refresh.
- `/api/auth/isLoggedIn` beantwortet er selbst (204 oder 401) ohne Serveraufruf.
- Verschwindet `kc_expires`, meldet er `SESSION_EXPIRED` an alle Tabs.

**Alternative (laut ADR):** Refresh in der Middleware oder reaktiv nach 401; verworfen, weil dann nicht jeder Aufruf abgedeckt ist und Fehler sichtbar würden.

**Konsequenzen:** Die gesamte Auth-Logik des Browsers hängt an Service-Worker-Verfügbarkeit und der Cookie-Store-API ([R-003](../11-risiken/R-003-abhaengigkeit-vom-service-worker-fuer-token-refresh.md)); das Verhalten ist nicht automatisch getestet ([QS-006](../10-qualitaet/QS-006-token-ablauf-ohne-sichtbaren-fehler.md)).
