---
id: RM-010
title: Nutzerdaten nur in Keycloak
status: entwurf
typ: roadmap
quellen: [GitHub-Ticket #296, backend/src/main/resources/db/migration/V2.0.0__remove_users_table.sql]
verknuepft: []
---

# RM-010: Nutzerdaten nur in Keycloak

**Liefergegenstand:** Identitätsdaten werden ausschließlich bei Keycloak geführt (#296). Teilweise umgesetzt: die lokale Benutzertabelle ist entfallen, Chronos hält noch eine Kopie von Name, E-Mail und Bild ([M-002](../05-modelle/M-002-fachdatenmodell.md)).

**Stand:** Ticket offen.
