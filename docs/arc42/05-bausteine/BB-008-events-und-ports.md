---
id: BB-008
title: Java-Backend: Events und Ports (Ebene 2)
status: entwurf
typ: baustein-ebene-2
quellen:
  - backend/.../application/events
  - backend/.../application/ports
verknuepft: [KZ-003, LS-006]
---

# Java-Backend: Events und Ports (Ebene 2)

**Events** (Java-`record`s, 26 Stück) nach Themen: Termin (`Created`, `Edited`, `Moved`, `Cancelled`, `Deleted`), Teilnahme (`Added`, `Removed`, `RoleChanged`, `StatusChanged`, `StatusConfirmation`, `StatusPendingReminder`, `StatusRecheckRequested`, `Invalid`), Gruppenteilnahme (`Added`, `Removed`), Freundschaft (`RequestSent`, `Accepted`, `Declined`, `Removed`), Gruppe (`Created`, `Deleted`, `NameChanged`, `MemberAdded`, `MemberRemoved`), `MessageSent`, `AppointmentReminder`.

**Ports:** `IdentityPort`, `NotificationPort`, `NotificationLogPort`, `ReminderEventPort` (+ `ReminderEventCDIAdapter`).
