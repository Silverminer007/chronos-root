# ADR-2026-10-22-015: Zusagen/Absagen über `POST /appointments/{id}/participation` (Ticket #206)

Status: umgesetzt. Betrifft `frontend-angular/` und das Nuxt-Frontend `frontend/`.

**Ursache:** `appointment.store` (Angular) und `stores/appointments.ts` (Nuxt) riefen `POST /api/v2/appointments/{id}/participants/approve|reject`
auf. Diese Routen gibt es in keinem Backend (Java, Rust); der Pfad wird als `/participants/{userId}` mit `userId="approve"` gematcht und
liefert 400 „Ungültiger Request-Body“. Beide Frontends teilten den Fehler, weil der Angular-Store 1:1 vom Nuxt-Store übernommen wurde und die
Unit-Tests nur den falschen Pfad bestätigten (der Mock kannte die Route, das Backend nicht).

**Entscheidung:** Der eigene RSVP geht an `POST /api/v2/appointments/{id}/participation` mit Body `{"status":"APPROVED"|"REJECTED"}`
(Backend-Vertrag, auf dem Cluster getestet). Der Store reicht den Status durch; Detailseite und Agenda-Karte nutzen denselben Store-Pfad.
Unit-Tests prüfen Pfad und Body, `e2e/rsvp.spec.ts` verwendet einen Mock, der auf die alten Routen wie das Backend mit 400 antwortet.

**Offen (nicht Teil dieses Fixes):** Die Java-Integrationstests (`ParticipationIntegrationTest` u. a.) und die k6-Szenarien unter `backend/` nennen noch
`/participants/approve|reject`; sie sollten gegen den tatsächlichen Vertrag geprüft werden.
