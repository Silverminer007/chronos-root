# ADR-035: Mindestanzahl als ausformulierte Frage, im Bearbeiten-Dialog nur ein Schalter

- Status: akzeptiert (Ticket #249)

## Kontext
Im Anlege-Stepper stand über „Ja“/„Nein“ nur die Überschrift „Mindestanzahl Teilnehmer“; „Nein“ war schwarz
(`btn-dark`), „Ja“ grün – zwei Farblogiken, der Zustand war nur an der Füllung erkennbar. Im Bearbeiten-Dialog war der
Text „Mindestteilnehmer festlegen“ ein zweiter `role="switch"`-Button neben dem eigentlichen Schalter (Screenreader:
zwei Schalter für ein Feld), und im Aus-Zustand stand nirgends, was das bedeutet.

## Entscheidung
- Stepper: Frage „Soll es eine Mindestanzahl geben?“, Hilfetext zum Zustand, die gewählte Option trägt Häkchen und
  `btn-primary` (beide Optionen gleiche Logik), die andere ist neutral umrandet; `aria-pressed` je Button.
- Bearbeiten-Dialog: ein einziger Schalter; der Text ist ein `<label for>` und zeigt den Zustand
  („Keine Mindestanzahl“ / „Mindestens N“). `toggleAttendees()` entfällt.
- Das Ja/Nein-Verhalten bleibt (keine Segment-Variante), nur Darstellung und Beschriftung ändern sich.
- Abgesichert durch `e2e/min-attendees-question.spec.ts` und das Komponenten-Spec des Dialogs.
