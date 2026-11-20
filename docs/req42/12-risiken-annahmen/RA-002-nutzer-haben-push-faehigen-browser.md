---
id: RA-002
title: Nutzer haben einen push-fähigen Browser
status: entwurf
typ: annahme
quellen: [frontend/app/pages/public/install.vue, frontend/app/composables/usePush.ts]
verknuepft: [RB-006, F-010, F-017]
---

# RA-002: Nutzer haben einen push-fähigen Browser

Annahme: Die meisten Nutzer verwenden ein Smartphone mit Browser, der Web Push unterstützt und dessen Erlaubnis sie erteilen. Auf iOS funktioniert das nur mit installierter App in Safari. Wer Push ablehnt oder nicht unterstützt, erhält keine Einladungen und Erinnerungen und wird nicht anders benachrichtigt (keine E-Mail); [Z-002](../01-ziele/Z-002-rueckmeldungen-ohne-nachfragen-erhalten.md) ist dann nicht erreicht.
