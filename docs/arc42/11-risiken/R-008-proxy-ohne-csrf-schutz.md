---
id: R-008
title: "Proxy ohne CSRF-Schutz"
status: entwurf
typ: risiko
quellen:
  - frontend/server/api/v2/[...path].ts
  - frontend/server/api/auth/callback.get.ts
  - frontend/server/api/auth/refresh.post.ts
verknuepft: [QA-004, AE-013]
---

# R-008: Proxy ohne CSRF-Schutz

**Risiko:** Die Cookies `kc_access` und `kc_refresh` werden mit `SameSite=None` gesetzt, der Proxy `server/api/v2/[...path].ts` authentifiziert allein über das Cookie und prüft weder `Origin` noch ein CSRF-Token. Eine fremde Webseite kann den Browser daher dazu bringen, Anfragen mit den Cookies der Nutzer:in an `/api/v2/*` zu senden; mit einfachen Anfragen ohne Vorab-Prüfung (z. B. Formular-POST ohne Body wie `…/cancel`) ist das ohne CORS-Freigabe möglich **(Ausnutzbarkeit nicht geprüft (?))**.

**Auswirkung:** Unbeabsichtigte Zustandsänderungen im Namen angemeldeter Benutzer (z. B. Termin absagen).

**Maßnahmen:** `SameSite=Lax` oder `Strict` setzen (prüfen, ob die Keycloak-Weiterleitung `callback` dann noch das Cookie erhält), oder `Origin`/`Sec-Fetch-Site` im Proxy prüfen.
