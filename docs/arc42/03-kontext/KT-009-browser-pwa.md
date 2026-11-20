---
id: KT-009
title: Nachbarsystem Browser und PWA
status: entwurf
typ: nachbarsystem
quellen:
  - frontend/public/manifest.webmanifest
  - frontend/public/push-sw.js
  - frontend/app/plugins/register-sw.client.ts
  - frontend/server/api/auth/callback.get.ts
  - frontend/app/composables/usePush.ts
verknuepft: [BB-003, LS-001, LS-004, FA-016]
---

# Nachbarsystem Browser und PWA

- **Rolle:** Laufzeitumgebung der SPA (`ssr: false`) als installierbare PWA mit Web-App-Manifest (`manifest.webmanifest`, Name „Chronos“, maskierbare Icons, Screenshots).
- **Browser-Schnittstellen:** Service Worker (`/push-sw.js`, Registrierung beim Start, Update-Prüfung stündlich), `cookieStore` zur Überwachung von `kc_expires`, Push- und Notification-API, `localStorage`.
- **Cookies:** `kc_access`, `kc_refresh` (`httpOnly`) und `kc_expires` (lesbar), alle `secure` und `sameSite=none` ([KZ-007](../08-konzepte/KZ-007-sicherheit-und-tokens.md)).
- **`localStorage`-Schlüssel:** `onboardingShownAt`, `pushPromptShownAt`, `pushPromptAnswer`, `shownFeatureIds` (Einmalhinweise, [BB-014](../05-bausteine/BB-014-frontend-stores.md)).
- **Seiten ohne Anmeldung:** `/public/install`, `/public/impressum`, `/public/datenschutz`.
