---
id: QS-017
title: "Token-Cookies für Skripte unlesbar"
status: entwurf
typ: qualitaetsszenario
quellen:
  - frontend/server/api/auth/callback.get.ts
  - frontend/server/api/auth/refresh.post.ts
  - frontend/server/api/v2/[...path].ts
verknuepft: [QA-009, AE-013, R-008]
---

# QS-017: Token-Cookies für Skripte unlesbar

| | |
|---|---|
| **Reiz** | Ein im Browser laufendes Skript (zum Beispiel eingeschleustes JavaScript) versucht, Access- oder Refresh-Token auszulesen. |
| **Antwort** | `kc_access` und `kc_refresh` sind `httpOnly` und `secure`; sie setzen `callback.get.ts` und `refresh.post.ts`. Lesbar ist nur `kc_expires` (Ablaufzeit, `httpOnly: false`). Das Backend erhält das Token ausschließlich über den Nuxt-Proxy als `Authorization`-Header ([AE-013](../09-entscheidungen/AE-013-nuxt-serverrouten-als-auth-proxy.md)). |
| **Messung** | `document.cookie` und der Cookie-Store des Service Workers enthalten weder `kc_access` noch `kc_refresh`; `kc_access` und `kc_refresh` tragen in beiden Routen `httpOnly: true`. |
| **Nachweis** | Kein automatischer Test **(?)**. Das Cookie-Attribut `SameSite=None` und fehlende Herkunftsprüfung schützen nicht vor Anfragen fremder Seiten ([R-008](../11-risiken/R-008-proxy-ohne-csrf-schutz.md)). |
