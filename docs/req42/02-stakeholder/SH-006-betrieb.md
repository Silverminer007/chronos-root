---
id: SH-006
title: Betrieb
status: entwurf
typ: stakeholder
quellen: [deployment/README.md, deployment/templates/prometheus-alerts.yaml, deployment/templates/postgres-backup.yaml]
verknuepft: []
---

# SH-006: Betrieb

Betreibt Chronos in den Umgebungen Produktion und Staging und ist Verantwortliche/r im Sinne der Datenschutzerklärung (Name aus der Konfiguration `NUXT_PUBLIC_IMPRESSUM_*`).

**Einfluss:** hoch auf Verfügbarkeit und Datenschutz (Zugriff auf Cluster und Geheimnisse). Person: [RA-013](../12-risiken-annahmen/RA-013-vom-product-owner-festzulegende-werte-und-entscheidungen.md).

**Erwartung:** Reproduzierbares Deployment, Alarme bei Ausfall, wiederherstellbare Datensicherung ([QA-006](../06-qualitaet/QA-006-zuverlaessigkeit-datensicherung.md), [QA-007](../06-qualitaet/QA-007-beobachtbarkeit.md)).

**Hilft bei der Analyse:** liefert Ressourcen- und Alarmwerte ([BM-001](../09-betriebsmittel-personal/BM-001-laufzeitinfrastruktur.md)). **Hindert:** Betrieb und Entwicklung liegen bei einer Person ([RA-010](../12-risiken-annahmen/RA-010-betrieb-und-entwicklung-haengen-an-einer-person.md)).
