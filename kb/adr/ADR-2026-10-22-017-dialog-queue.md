# ADR-2026-10-22-017: Layout-Dialoge erscheinen nacheinander über `DialogQueueService` (Ticket #208)

Status: umgesetzt. Betrifft `frontend-angular/src/app/layout/`.

**Ursache:** `DefaultLayoutComponent` rendert `PwaInstallDialog`, `PushPermissionModal` und `NewFeaturesDialog` nebeneinander; jeder zeigt sich
unabhängig mit eigenem `modal-backdrop` und `.modal`. Beim Erststart waren Neuigkeiten und Push-Abfrage gleichzeitig offen, der später im DOM
stehende Neuigkeiten-Dialog (gleiche z-index) deckte den Push-Dialog ab und fing die Klicks ab; derselbe Defekt drohte mit dem PWA-Dialog.

**Entscheidung:** `DialogQueueService` (Signal-basiert) lässt nur einen Dialog gleichzeitig sichtbar sein. Dialoge melden sich synchron in
`ngOnInit` mit `claim` an (Push auch vor der asynchronen Prüfung, damit nichts aufblitzt) und geben mit `release` frei. Reihenfolge:
Push → Neuigkeiten → PWA-Installation. Neue Layout-Dialoge müssen sich hier eintragen (`DIALOG_PRIORITY`). Verworfen: z-index-Stapelung,
weil zwei Backdrops übereinander schlecht bedienbar sind und der Nuxt-Stand dasselbe sequentielle Verhalten erwartet.
