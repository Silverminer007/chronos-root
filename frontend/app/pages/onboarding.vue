<template>
  <div class="min-h-screen relative overflow-hidden">
    <!-- Background -->
    <div
        class="absolute inset-0 bg-linear-to-br from-purple-600 via-pink-500 to-orange-400 dark:from-purple-900 dark:via-pink-900 dark:to-orange-900">
      <div class="absolute inset-0 opacity-20">
        <div
            class="absolute top-20 left-10 w-72 h-72 bg-white rounded-full mix-blend-overlay filter blur-xl animate-pulse"></div>
        <div
            class="absolute bottom-20 right-10 w-96 h-96 bg-white rounded-full mix-blend-overlay filter blur-xl animate-pulse animation-delay-1000"></div>
      </div>
    </div>

    <div class="relative z-10 min-h-screen flex flex-col items-center justify-center px-4 py-12">
      <!-- Logo -->
      <div class="flex items-center gap-2 mb-8">
        <img src="/icons/icon.png" alt="Chronos Logo" class="w-8 h-8 rounded-lg shadow-lg"/>
        <span class="text-xl font-bold text-white">Chronos</span>
      </div>

      <!-- Progress dots -->
      <div class="flex gap-2 mb-8">
        <button
            v-for="(_, i) in steps"
            :key="i"
            @click="currentStep = i"
            class="w-2.5 h-2.5 rounded-full transition-all duration-300"
            :class="i === currentStep ? 'bg-white scale-125' : 'bg-white/40 hover:bg-white/60'"
        />
      </div>

      <!-- Step card -->
      <div
          class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-2xl shadow-2xl p-8 sm:p-12 max-w-lg w-full text-center transition-all duration-300">
        <div
            class="w-16 h-16 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-2xl flex items-center justify-center mx-auto mb-6">
          <Icon :name="steps[currentStep].icon" class="text-purple-600 dark:text-purple-400 text-3xl"/>
        </div>

        <h1 class="text-2xl sm:text-3xl font-bold text-gray-800 dark:text-gray-100 mb-3">
          {{ steps[currentStep].title }}
        </h1>

        <p class="text-gray-600 dark:text-gray-300 text-base sm:text-lg leading-relaxed">
          {{ steps[currentStep].description }}
        </p>
      </div>

      <!-- Navigation -->
      <div class="flex items-center gap-4 mt-8">
        <button
            v-if="currentStep > 0"
            @click="currentStep--"
            class="px-5 py-2.5 rounded-lg font-medium text-white/80 hover:text-white border border-white/30 hover:border-white/60 transition-all"
        >
          Zurück
        </button>

        <button
            v-if="currentStep < steps.length - 1"
            @click="currentStep++"
            class="px-6 py-2.5 rounded-lg font-semibold text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 shadow-lg transition-all transform hover:scale-105"
        >
          Weiter
        </button>

        <button
            v-else
            @click="completeOnboarding()"
            class="px-6 py-2.5 rounded-lg font-semibold text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 shadow-lg transition-all transform hover:scale-105"
        >
          Loslegen
        </button>
      </div>

      <!-- Skip -->
      <NuxtLink
          v-if="currentStep < steps.length - 1"
          to="/agenda"
          class="mt-4 text-white/60 hover:text-white/90 text-sm transition-colors"
      >
        Überspringen
      </NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({
  layout: false
})

import {useOnboarding} from "~/composables/useOnboarding";

const currentStep = ref(0)

const steps = [
  {
    icon: 'lucide:party-popper',
    title: 'Willkommen bei Chronos!',
    description: 'Schön, dass du dabei bist. Lass uns kurz zeigen, was Chronos für dich und dein Team kann.'
  },
  {
    icon: 'lucide:calendar',
    title: 'Termine verwalten',
    description: 'Erstelle Termine, lade Mitglieder ein und behalte den Überblick über alle anstehenden Events.'
  },
  {
    icon: 'lucide:user',
    title: 'Freunde & Familie',
    description: 'Lade Freunde und Familie ein, um sie zu Terminen hinzuzufügen.'
  },
  {
    icon: 'lucide:users',
    title: 'Teams & Gruppen',
    description: 'Erstelle Gruppen, um mehrere Freunde gleichzeitig zu einem Termin hinzuzufügen'
  },
  {
    icon: 'lucide:users',
    title: 'Rollen & Verantwortlichkeiten',
    description: 'Weise Teilnehmenden eine Rolle für den Termin zu - von Gast bis Organisator'
  },
  {
    icon: 'lucide:bell',
    title: 'Immer informiert',
    description: 'Erhalte Push-Benachrichtigungen bei neuen Terminen, Änderungen und Zu- oder Absagen.'
  }
]

function completeOnboarding() {
  const {markShown} = useOnboarding();
  markShown()
  navigateTo('/agenda')
}
</script>

<style scoped>
.animation-delay-1000 {
  animation-delay: 1s;
}
</style>
