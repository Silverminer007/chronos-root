---
id: AE-022
title: "Participation status endpoint (Java parity)"
status: entwurf
typ: architekturentscheidung
adr-status: accepted
datum: 2026-10-14
quellen:
  - kb/adr/ADR-2026-10-14-016-participation-status-endpoint.md
verknuepft: []
---

# AE-022: Participation status endpoint (Java parity)

Quelle: `kb/adr/ADR-2026-10-14-016-participation-status-endpoint.md` (Status im ADR: accepted, Datum: 2026-10-14). Die folgenden Abschnitte sind gekürzte Auszüge des (englischen) ADR; maßgeblich ist das ADR.

## Kontext

Issue #121 requires `GET /api/v2/appointments/{id}/participation/status` in the Rust backend. The Java backend already serves it (`AppointmentParticipationResource#getParticipationStatus`, backed by `AppointmentParticipationQueryService#getUserStatus`). The acceptance criteria require the request/response format and authorization to match Java exactly.

The ticket thread contains earlier answers (2026-10-04) that proposed 404 for non-participants and for soft-deleted appointments, and a minimal body. Those answers conflict with the Java reference. The later answer (2026-10-13) resolves the conflict in favour of Java parity, and this ADR records that choice.

## Entscheidung

Match the Java backend exactly:

### 1. Always 200 OK, PENDING by default

- A caller without a participation row receives `PENDING` (Java: `.orElse(ParticipationStatus.PENDING)`).
- The endpoint does not return 404 for unknown appointments or non-participants.

### 2. No existence or soft-delete check

- The query reads `appointment_participation` only. It does not join `appointments` and does not filter on `status`.
- **Deliberate exception** to the read-filter rule in [ADR-2026-10-03-011](../../../kb/adr/ADR-2026-10-03-011-appointment-mutations.md) (which excludes CANCELLED and DELETED from read endpoints). This endpoint returns the caller's status for cancelled and deleted appointments too, because Java does.

### 3. Caller's own status only

- The path has no user parameter. The response always describes the authenticated principal.
- Any authenticated user may call it …

## Konsequenzen

### Positive

- ✅ Frontend can switch between Java and Rust backends for this call without changes.
- ✅ The default lives in the service, so the repository stays a plain lookup.

### Negative

- ❌ A caller can still read their own status for a cancelled or deleted appointment (200 instead of 404). This departs from the ADR-011 read rule. It discloses nothing new: an unknown appointment also returns `PENDING`, so the two cases cannot be told apart.
- ❌ Because a non-participant also gets `PENDING`, a client cannot distinguish "not invited" from "invited, no response". Matches Java.
