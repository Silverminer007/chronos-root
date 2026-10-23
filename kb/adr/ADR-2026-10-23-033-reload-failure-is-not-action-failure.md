# ADR-033: Scheiternder Reload nach gelungener Aktion ist kein Fehler der Aktion

- Status: akzeptiert (Ticket #246)

## Kontext
`AppointmentStore.mutate()` sendete die Änderung und lud im selben `try` den Termin neu. Scheiterte nur der Reload
(z. B. 404 des Rust-Backends für abgesagte Termine), wurde der Fehler weitergeworfen; die Komponenten zeigten
„Termin konnte nicht abgesagt werden“ o. Ä., obwohl die Aktion im Backend gelungen war. Dasselbe Muster steckte in
`GroupStore` (`addMember`, `removeMember`) und `FriendshipStore` (`sendRequest`, `acceptRequest`).

## Entscheidung
Aktion und Reload getrennt behandeln: Nur ein Fehler der Aktion wird geworfen. Der Reload-Fehler wird verschluckt
(`.catch(() => undefined)`); die Lade-Methoden setzen `error()` selbst, der Zustand bleibt der letzte bekannte.

## Nicht Teil dieser Änderung
Ausblenden der Aktionen auf abgesagten Terminen (siehe ADR-032) und bessere Standard-Fehlertexte.
