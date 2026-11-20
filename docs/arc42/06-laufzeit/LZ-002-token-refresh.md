---
id: LZ-002
title: Token-Refresh und Sitzungsende
status: entwurf
typ: laufzeitszenario
quellen:
  - frontend/public/push-sw.js
  - frontend/server/api/auth/refresh.post.ts
  - frontend/app/plugins/auth.client.ts
verknuepft: [BB-003, LS-004, QA-004]
---

# Token-Refresh und Sitzungsende

```mermaid
sequenceDiagram
  participant A as App
  participant SW as Service Worker
  participant N as Nuxt-Server
  A->>SW: GET /api/v2/appointments
  alt kc_expires fehlt oder Restlaufzeit unter 5 s
    SW->>N: POST /api/auth/refresh
    N-->>SW: neue Cookies
  end
  SW->>N: Anfrage
  N-->>SW: Antwort
  opt 401
    SW->>N: POST /api/auth/refresh
    SW->>N: Anfrage wiederholen
  end
  SW-->>A: Antwort
```

Unabhängig von Anfragen plant der Service Worker beim Aktivieren und bei jeder Änderung von `kc_expires` einen Refresh 30 s vor Ablauf. Schlägt der Refresh fehl, antwortet der Server mit 401 (kein oder ungültiges Refresh-Token, Cookies werden gelöscht) oder 502 (ungültige Keycloak-Antwort). Wird `kc_expires` gelöscht, sendet der Service Worker `SESSION_EXPIRED` an alle Fenster, und `auth.client.ts` navigiert auf `/`. Schlägt das Wiederholen nach einer 401 fehl, gibt der Service Worker die ursprüngliche 401 zurück.
