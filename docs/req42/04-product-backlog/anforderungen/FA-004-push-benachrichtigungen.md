---
id: FA-004
title: Push-Benachrichtigungen
status: entwurf
typ: anforderung
quellen: [backend/src/main/java/de/chronos_live/chronos_date_api/application/WebPushService.java, backend/src/main/java/de/chronos_live/chronos_date_api/infrastructure/WebPushAdapter.java, frontend/public/push-sw.js, frontend/app/composables/usePush.ts]
verknuepft: [F-010]
---

# FA-004: Push-Benachrichtigungen

*Als Teilnehmer möchte ich Ereignisse sofort als Push erhalten, damit ich nichts verpasse.*

## Akzeptanzkriterien
- Eine Person kann mehrere Geräte abonnieren und einzeln abbestellen; der Abostatus eines Geräts ist abrufbar; ein erneutes Abonnieren desselben Geräts ersetzt das alte Abo. Die Oberfläche fragt vor dem ersten Abonnieren per Dialog nach der Erlaubnis.
- Welches Ereignis wen erreicht, regelt [M-007](../../05-modelle/M-007-benachrichtigungsmatrix.md); die Einstellungen der Empfänger gelten ([FA-012](FA-012-benachrichtigungen-konfigurieren.md)).
- Die auslösende Person erhält keine Benachrichtigung zu ihrer eigenen Aktion.
- Meldet der Push-Dienst ein abgelaufenes Abo (`404`/`410`) oder schlägt der Versand fehl, wird das Abo entfernt.
- Jeder Zustellversuch wird mit Nutzer, Ereignistyp, Ergebnis und Fehlertext protokolliert ([FA-007](FA-007-admin-api.md)).
- Ist der Betrieb ohne Push-Schlüssel konfiguriert, wird kein Push gesendet und die Funktion schlägt nicht fehl.
- Bei Verschiebung, ausstehender Rückmeldung, zu wenigen Teilnehmern und Rückfrage bietet die Benachrichtigung Schnellaktionen „Zusagen“ und „Absagen“ (nur die zum aktuellen Status passende); ein Tippen auf die Benachrichtigung öffnet den Termin, die Gruppe oder die Umfrage.
