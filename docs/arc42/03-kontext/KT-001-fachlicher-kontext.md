---
id: KT-001
title: Fachlicher Kontext
status: entwurf
typ: kontext
quellen:
  - CLAUDE.md
  - kb/glossary/begriffe.md
verknuepft: [FA-001, FA-002, FA-003, FA-004, Z-001]
---

# Fachlicher Kontext

Chronos plant Termine für Jugendorganisationen. Fachliche Akteure und Austausch:

```mermaid
flowchart LR
  L[Verantwortliche/r<br/>RESPONSIBLE] -- legt an, lädt ein, verschiebt, sagt ab --> C((Chronos))
  T[Teilnehmer/Helfer] -- sagt zu/ab, schreibt Nachrichten --> C
  C -- Push, Erinnerung, Systemnachricht --> T
  A[Administrator<br/>ADMIN_API] -- verwaltet Nutzer, Gruppen, Termine, Push --> C
```

| Akteur | Schnittstelle zu Chronos |
|---|---|
| Verantwortliche/r | Termin anlegen/ändern/verschieben/absagen, Teilnehmer und Gruppen einladen, Rollen ändern |
| Teilnehmer / Helfer | Einladung beantworten (`APPROVED`, `REJECTED`, `PENDING`), Termin-Chat, Push-Einstellungen |
| Administrator | Admin-API `/api/v2/admin/*`, Admin-Seiten im Frontend |
| Freunde | Freundschaftsanfragen, Gruppen (Voraussetzung zum Einladen) |
