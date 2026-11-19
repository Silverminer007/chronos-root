---
id: BB-003
title: Frontend: Service Worker (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - frontend/public/push-sw.js
verknuepft: [LS-004, LZ-002, KT-009]
---

# Frontend: Service Worker (Ebene 2)

Fängt alle `/api/*`-Anfragen ab:
- `/api/auth/isLoggedIn` wird synthetisch aus `cookieStore` beantwortet (204/401).
- `/api/v2/*`: Refresh bei < 5 s Restlaufzeit, Wiederholung nach 401.
- `/api/auth/*`: unverändert durchgereicht.
- Plant einen Refresh 30 s vor `kc_expires`, meldet `SESSION_EXPIRED` an alle Tabs, dedupliziert Refreshes (`inflightRefresh`) und zeigt Push-Nachrichten an.
