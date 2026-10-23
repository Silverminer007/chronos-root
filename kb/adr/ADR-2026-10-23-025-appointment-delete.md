# ADR-2026-10-23-025: Termin löschen auf der Termindetailseite

Status: akzeptiert (Ticket #237)

## Kontext
Das alte Frontend hat keine Oberfläche zum Löschen eines Termins (nur die ungenutzte Store-Methode). Der Nutzer hat
entschieden, dass das Angular-Frontend diese Funktion erhält. `AppointmentStore.deleteAppointment` (`DELETE /api/v2/appointments/{id}`)
existiert bereits und entfernt den Termin aus Liste und Detailansicht.

## Entscheidung
- In `AppointmentResponsibleActionsComponent` (Karte „Organisator-Aktionen“, nur für Organisatoren) kommt unter „Termin absagen“
  der Button „Termin löschen“ (`btn-outline-danger`, Icon `bi-trash`).
- Klick öffnet den vorhandenen `ModalService.confirm` (rot): „Termin endgültig löschen? Das kann nicht rückgängig gemacht werden.“
- Bestätigt: Store-Aufruf, Toast „Termin gelöscht“, `router.navigateByUrl('/agenda')` (wie `back-navigation.ts`).
- Fehler: Toast „Fehler“ mit der Backend-Meldung bzw. dem deutschen Fallback „Termin konnte nicht gelöscht werden“
  (`getErrorMessage`); die Seite bleibt.
- Anders als „Termin absagen“ ist der Button auch bei abgesagten Terminen aktiv (Löschen ist dort sinnvoll). Während einer
  laufenden Aktion sind beide Buttons über `busy` gesperrt.
- Der Store bleibt unverändert; seine Tests (Pfad/Methode, Fehlerfall) bestanden bereits.

## Test
Unit: `appointment-responsible-actions.component.spec.ts`; E2E: `e2e/appointment-delete.spec.ts` (gemockter BFF).
