# ADR-2026-10-22-001: Nachricht-Button auf der Agenda-Karte (Ticket #209)

Status: akzeptiert. Ergänzt ADR-2026-10-21-007.

**Ursache:** ADR-2026-10-21-007 hatte den Nachrichten-Dialog bewusst aus der Agenda-Karte ausgeklammert („kommt mit Termindetails“). Als `MessageDialogComponent` und `AppointmentStore.sendMessage` mit der Detailseite (`MessagesCardComponent`) existierten, wurde die Karte nicht nachgezogen. Die Karte hatte dadurch nur Zusagen/Absagen, die Nuxt-Karte drei Aktionen.

**Entscheidung:**
- `AppointmentCardComponent` zeigt zwischen Zusagen und Absagen den Button „Nachricht“ (Papierflieger, schmal nur Icon, Text `visually-hidden`) und rendert pro Karte einen `app-message-dialog` für den Termin der Karte. Senden über `AppointmentStore.sendMessage` (`POST /appointments/{id}/messages`), Toast „Nachricht versendet“ bzw. Fehler-Toast wie in `MessagesCardComponent`; der Dialog bleibt bei Fehlern offen.
- Die Dialog-Logik wird nicht in eine gemeinsame Komponente gezogen: Karte und Nachrichten-Karte teilen bereits `shared/MessageDialogComponent`, der Rest sind ~10 Zeilen.
- Tests: Unit-Tests in `appointment-card.component.spec.ts`, E2E `e2e/agenda-message-button.spec.ts`.

**Suche nach gleichem Defekt:** Die übrigen in ADR-2026-10-21-007 zurückgestellten Nuxt-Funktionen (FAB zum Anlegen) sind eigene Tickets und hier nicht berührt.
