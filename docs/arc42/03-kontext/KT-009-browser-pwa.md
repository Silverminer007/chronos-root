---
id: KT-009
title: Nachbarsystem Browser und PWA
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/public/push-sw.js
  - frontend/app/pages/public/install.vue
verknuepft: [BB-003, LZ-002]
---

# Nachbarsystem Browser und PWA

- **Rolle:** Laufzeitumgebung der SPA (`ssr: false`) als installierbare PWA.
- **Schnittstellen:** Service Worker (`push-sw.js`) fängt `/api/*` ab, kennt `cookieStore` und zeigt Push-Nachrichten; Cookies `kc_access`, `kc_refresh` (httpOnly) und `kc_expires`.
- **Installationshinweise:** `/public/install`; rechtliche Seiten `/public/impressum`, `/public/datenschutz`.
