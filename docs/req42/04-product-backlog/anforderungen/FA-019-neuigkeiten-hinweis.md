---
id: FA-019
title: Neuigkeiten-Hinweis
status: entwurf
typ: anforderung
quellen: [frontend/app/composables/useNewFeatures.ts, frontend/app/components/NewFeaturesDialog.vue]
verknuepft: [F-020]
---

# FA-019: Neuigkeiten-Hinweis

Siehe das zugehörige Feature ([F-020](../features/F-020-neuigkeiten-hinweis.md)) für die Story.

## Akzeptanzkriterien
- Beim Aufruf der App erscheint ein Dialog mit allen Neuigkeiten, die dieser Browser noch nicht gesehen hat (je Eintrag Titel, Beschreibung und Sprunglink); als gesehen markierte Einträge erscheinen nicht wieder.
- Der Dialog enthält derzeit vier Einträge: Profil, verknüpfte Konten, Passkeys und Passwort ändern, jeweils mit Sprunglink zum Profil.
- Die Markierung liegt im Browser (`localStorage`), nicht am Konto; auf einem neuen Gerät erscheinen alle Einträge erneut.
