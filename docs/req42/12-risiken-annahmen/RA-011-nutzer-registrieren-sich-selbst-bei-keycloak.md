---
id: RA-011
title: Nutzer registrieren sich selbst bei Keycloak
status: entwurf
typ: annahme
quellen: [frontend/server/api/auth/register.get.ts]
verknuepft: [RB-002, FA-013]
---

# RA-011: Nutzer registrieren sich selbst bei Keycloak

Annahme: Das Keycloak-Realm erlaubt Selbstregistrierung (die Landingpage verlinkt „Registrieren“ auf die Keycloak-Registrierung; die Realm-Einstellung liegt außerhalb des Repositorys **(?)**), und jeder Registrierte darf Chronos nutzen: Das Backend akzeptiert jedes gültige Token des Realms, eine Freigabe neuer Benutzer gibt es nicht. Ob Registrierungen eingeschränkt sein sollen, entscheidet der Product Owner.
