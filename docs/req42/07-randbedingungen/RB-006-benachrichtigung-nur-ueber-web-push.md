---
id: RB-006
title: Benachrichtigung nur über Web Push
status: entwurf
typ: randbedingung
kategorie: technisch
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/WebPushAdapter.java, frontend/app/pages/public/install.vue]
verknuepft: []
---

# RB-006: Benachrichtigung nur über Web Push

Benachrichtigungen werden ausschließlich als Web Push (mit VAPID-Schlüsseln des Betreibers) an Browser zugestellt; es gibt keine E-Mail, SMS oder native App. Folge: Push erfordert Browserunterstützung und Erlaubnis der Person, auf iOS die Installation als App in Safari ([RA-002](../12-risiken-annahmen/RA-002-nutzer-haben-push-faehigen-browser.md)).
