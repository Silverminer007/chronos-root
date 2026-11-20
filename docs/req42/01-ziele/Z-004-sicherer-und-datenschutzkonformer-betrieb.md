---
id: Z-004
title: Sicherer und datenschutzkonformer Betrieb
status: entwurf
typ: ziel
quellen: [frontend/app/pages/public/datenschutz.vue, frontend/app/pages/public/impressum.vue]
verknuepft: [SH-001, SH-006]
---

# Z-004: Sicherer und datenschutzkonformer Betrieb

**Ziel:** Chronos so betreiben, dass Zugangsdaten und personenbezogene Daten geschützt sind und Impressum sowie Datenschutzerklärung ohne Anmeldung einsehbar sind.

**Vorteil:** Rechtssicherer, vertrauenswürdiger Betrieb.

**Metrik:** Anzahl bekannter Abweichungen zwischen Datenschutzerklärung und Anwendung (Ist: fünf, siehe [RA-004](../12-risiken-annahmen/RA-004-datenschutzerklaerung-verspricht-mehr-als-die-anwendung-leistet.md); Soll: 0) und Erreichbarkeit von Impressum und Datenschutzerklärung ohne Anmeldung (automatisierter E2E-Test, [FA-017](../04-product-backlog/anforderungen/FA-017-impressum-und-datenschutzerklaerung-bereitstellen.md)). Weitere Datenschutzziele wie Löschfristen: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

Rechtlicher Rahmen: [RB-014](../07-randbedingungen/RB-014-datenschutz-nach-dsgvo-und-impressumspflicht.md); Sicherheitsqualität: [QA-009](../06-qualitaet/QA-009-sicherheit-zugangstoken-nicht-fuer-skripte-lesbar.md).
