---
id: RA-004
title: Datenschutzerklärung verspricht mehr, als die Anwendung leistet
status: entwurf
typ: risiko
quellen: [frontend/app/pages/public/datenschutz.vue, backend/src/main/java/de/chronos_live/chronos_date_api/presentation/UserResource.java, frontend/server/api/auth/callback.get.ts, frontend/nuxt.config.ts]
verknuepft: [Z-004, RB-014, FA-014, FA-017]
---

# RA-004: Datenschutzerklärung verspricht mehr, als die Anwendung leistet

**Risiko:** Die Datenschutzerklärung sagt zu, (1) alle personenbezogenen Daten würden nach Löschung des Kontos binnen 30 Tagen gelöscht, (2) die Cookies `kc_access`/`kc_refresh` hätten 30 Tage Dauer, (3) Daten gingen nicht an Dritte, (4) IP-Adressen würden anonymisiert, (5) die E-Mail-Adresse werde für Benachrichtigungen verwendet. Im Code: (1) Eine Selbstlöschung gibt es nicht; die Administratorlöschung entfernt nur das Keycloak-Konto, Teilnahmen, Nachrichten und die Profilkopie bleiben. (2) Die Cookie-Laufzeit richtet sich nach der Token-Gültigkeit, und `kc_expires` fehlt in der Aufzählung. (3) Die Browser-Push-Dienste und der Google-Login ([SC-001](../03-scope/SC-001-systemkontext-akteure-und-nachbarsysteme.md)) sind nicht erwähnt. (4) Eine IP-Anonymisierung ist im Code nicht belegt **(?)**. (5) Benachrichtigungen gehen nur per Push ([RB-006](../07-randbedingungen/RB-006-benachrichtigung-nur-ueber-web-push.md)); die E-Mail-Adresse dient der Anmeldung.

**Wahrscheinlichkeit / Schaden:** hoch (Abweichungen (1), (2) und (5) sind im Code belegt) / mittel bis hoch (Abmahn- und Vertrauensrisiko). **Maßnahme:** Erklärung und Anwendung angleichen (Löschfunktion oder Erklärung anpassen); rechtlich prüfen lassen.
