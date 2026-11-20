---
id: FA-017
title: Impressum und Datenschutzerklärung bereitstellen
status: entwurf
typ: anforderung
quellen: [frontend/app/pages/public/impressum.vue, frontend/app/pages/public/datenschutz.vue, frontend/app/components/LandingFooter.vue, frontend/e2e/public-pages.spec.ts]
verknuepft: [F-018]
---

# FA-017: Impressum und Datenschutzerklärung bereitstellen

*Als Besucher möchte ich Impressum und Datenschutzerklärung ohne Anmeldung lesen, damit ich weiß, wer verantwortlich ist und wie Daten verarbeitet werden.*

## Akzeptanzkriterien
- `/public/impressum` und `/public/datenschutz` sind ohne Anmeldung erreichbar und aus der Fußzeile der Landingpage verlinkt (E2E-Test für Impressum, Überschrift und Rücklink zur Startseite).
- Das Impressum zeigt Name, Straße und Ort aus der Konfiguration (`NUXT_PUBLIC_IMPRESSUM_*`) sowie E-Mail und Telefon, sofern gesetzt; fehlende Pflichtangaben erscheinen als „[… nicht konfiguriert]“.
- Die Datenschutzerklärung enthält die Abschnitte Verantwortlicher, erhobene Daten, Cookies, Zweck, Rechtsgrundlage, Weitergabe, Speicherdauer, Rechte, Datensicherheit und Änderungen; der Verantwortliche stammt aus derselben Konfiguration.
- Ob der Inhalt der Datenschutzerklärung der Anwendung entspricht, ist offen ([RA-004](../../12-risiken-annahmen/RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md)).
