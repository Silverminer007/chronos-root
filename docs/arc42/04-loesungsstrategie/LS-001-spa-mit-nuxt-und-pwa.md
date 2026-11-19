---
id: LS-001
title: SPA mit Nuxt 3/Vue 3 als PWA
status: entwurf
typ: strategie
quellen:
  - kb/adr/ADR-2025-09-28-001-nuxt-vue.md
  - frontend/nuxt.config.ts
verknuepft: [BB-001, KT-009]
---

# SPA mit Nuxt 3/Vue 3 als PWA

Das Frontend ist eine reine SPA (`ssr: false`) auf Nuxt/Vue 3 mit `<script setup lang="ts">`, PrimeVue und Tailwind. Gründe laut ADR-001: eine Codebasis für Web und installierbare PWA, Push über Service Worker. Konsequenz: alle Daten werden clientseitig geladen (`useFetch` zum Lesen, `$fetch` in Store-Aktionen).
