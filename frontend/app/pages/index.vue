<template>
  <div class="min-h-screen bg-linear-to-br relative overflow-hidden transition-colors duration-300">
    <!-- Animated Background -->
    <div class="absolute inset-0 bg-linear-to-br from-purple-600 via-pink-500 to-orange-400 dark:from-purple-900 dark:via-pink-900 dark:to-orange-900 opacity-100">
      <div class="absolute inset-0 opacity-20">
        <div class="absolute top-20 left-10 w-72 h-72 bg-white rounded-full mix-blend-overlay filter blur-xl animate-pulse"></div>
        <div class="absolute bottom-20 right-10 w-96 h-96 bg-white rounded-full mix-blend-overlay filter blur-xl animate-pulse animation-delay-1000"></div>
      </div>
    </div>

    <div class="relative z-10">
      <!-- Header -->
      <header class="container mx-auto px-4 sm:px-6 py-4 sm:py-6">
        <nav class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-2 sm:gap-3 shrink-0">
            <img src="/icons/icon.png" alt="Chronos Logo" class="w-8 h-8 sm:w-10 sm:h-10 rounded-lg shadow-lg" />
            <span class="text-xl sm:text-2xl font-bold text-white">Chronos</span>
          </div>
          <div class="flex gap-2 sm:gap-3 shrink-0">
            <a href="/api/auth/login" class="px-3 sm:px-6 py-2 sm:py-2.5 text-sm sm:text-base rounded-lg font-medium transition-all shadow-md border-2 border-purple-600 dark:border-purple-400 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 bg-white dark:bg-neutral-800 whitespace-nowrap">
              Anmelden
            </a>
            <a href="/api/auth/register" class="px-3 sm:px-6 py-2 sm:py-2.5 text-sm sm:text-base rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 dark:hover:from-purple-600 dark:hover:to-pink-500 whitespace-nowrap">
              Registrieren
            </a>
          </div>
        </nav>
      </header>

      <!-- Hero Section -->
      <section class="container mx-auto px-4 sm:px-6 py-12 sm:py-20 text-center">
        <div class="max-w-4xl mx-auto">
          <div class="inline-flex items-center gap-2 bg-white/20 dark:bg-white/10 backdrop-blur-sm px-3 sm:px-4 py-2 rounded-full mb-4 sm:mb-6">
            <i class="pi pi-bolt text-white text-sm sm:text-base"></i>
            <span class="text-white text-xs sm:text-sm font-medium">Für Jugendverbände gemacht</span>
          </div>

          <h1 class="text-3xl sm:text-5xl md:text-7xl font-bold text-white mb-4 sm:mb-6 leading-tight px-4">
            Terminplanung,<br />die funktioniert
          </h1>

          <p class="text-base sm:text-xl md:text-2xl text-white/90 mb-8 sm:mb-10 max-w-2xl mx-auto px-4">
            Verwalte Team-Termine, prüfe Verfügbarkeiten und behalte den Überblick –
            einfach, modern und zuverlässig.
          </p>

          <div class="flex flex-col sm:flex-row gap-3 sm:gap-4 justify-center px-4">
            <a href="/api/auth/register" class="px-6 sm:px-8 py-3 sm:py-4 rounded-lg font-semibold text-white transition-all shadow-xl text-base sm:text-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 dark:hover:from-purple-600 dark:hover:to-pink-500 transform hover:scale-105">
              Jetzt kostenlos starten
            </a>
            <button @click="scrollToFeatures" class="px-6 sm:px-8 py-3 sm:py-4 rounded-lg font-semibold transition-all shadow-lg text-base sm:text-lg border-2 border-purple-600 dark:border-purple-400 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 bg-white dark:bg-neutral-800 transform hover:scale-105">
              Mehr erfahren
            </button>
          </div>
        </div>

        <!-- Mock App Preview -->
        <div class="mt-12 sm:mt-16 max-w-5xl mx-auto px-4">
          <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl sm:rounded-2xl shadow-2xl p-4 sm:p-8 transform hover:scale-105 transition-transform duration-300">
            <div class="bg-linear-to-br from-gray-50 to-gray-100 dark:from-neutral-700 dark:to-neutral-700 rounded-lg sm:rounded-xl p-4 sm:p-8 min-h-64 sm:min-h-96">
              <div class="flex items-center gap-2 sm:gap-4 mb-4 sm:mb-6">
                <div class="w-2 h-2 sm:w-3 sm:h-3 rounded-full bg-red-500"></div>
                <div class="w-2 h-2 sm:w-3 sm:h-3 rounded-full bg-yellow-500"></div>
                <div class="w-2 h-2 sm:w-3 sm:h-3 rounded-full bg-green-500"></div>
              </div>
              <div class="space-y-3 sm:space-y-4">
                <AppointmentDemoCard :appointment="demoAppointment1"/>
                <AppointmentDemoCard :appointment="demoAppointment2"/>
                <AppointmentDemoCard :appointment="demoAppointment3"/>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Features Section -->
      <section id="features" class="container mx-auto px-4 sm:px-6 py-12 sm:py-20">
        <div class="text-center mb-12 sm:mb-16 px-4">
          <h2 class="text-3xl sm:text-4xl md:text-5xl font-bold text-white mb-3 sm:mb-4">
            Alles, was dein Team braucht
          </h2>
          <p class="text-base sm:text-xl text-white/80 max-w-2xl mx-auto">
            Moderne Funktionen für effiziente Teamorganisation
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6 max-w-7xl mx-auto">
          <div
              v-for="(feature, index) in features"
              :key="index"
              class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl p-5 sm:p-6 transform hover:scale-105 transition-all duration-300 hover:shadow-2xl"
          >
            <div class="w-12 h-12 sm:w-14 sm:h-14 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center mb-3 sm:mb-4">
              <i :class="feature.icon" class="text-purple-600 dark:text-purple-400 text-xl sm:text-2xl"></i>
            </div>
            <h3 class="text-lg sm:text-xl font-bold text-gray-800 dark:text-gray-100 mb-2">
              {{ feature.title }}
            </h3>
            <p class="text-sm sm:text-base text-gray-600 dark:text-gray-300">
              {{ feature.description }}
            </p>
          </div>
        </div>
      </section>

      <!-- CTA Section -->
      <section class="container mx-auto px-4 sm:px-6 py-12 sm:py-20">
        <div class="bg-white/90 dark:bg-neutral-900/90 backdrop-blur-sm border-2 border-purple-200 dark:border-purple-600 rounded-xl sm:rounded-2xl p-8 sm:p-12 text-center max-w-4xl mx-auto shadow-2xl">
          <h2 class="text-2xl sm:text-4xl font-bold text-gray-800 dark:text-gray-100 mb-3 sm:mb-4">
            Bereit loszulegen?
          </h2>
          <p class="text-base sm:text-xl text-gray-600 dark:text-gray-300 mb-6 sm:mb-8">
            Starte noch heute und bringe Ordnung in eure Terminplanung
          </p>
          <a href="/api/auth/register" class="px-8 sm:px-10 py-3 sm:py-4 rounded-lg font-semibold text-white transition-all shadow-xl text-base sm:text-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 dark:hover:from-purple-600 dark:hover:to-pink-500 transform hover:scale-105">
            Kostenlos registrieren
          </a>
        </div>
      </section>

      <!-- Footer -->
      <footer class="container mx-auto px-4 sm:px-6 py-6 sm:py-8 text-center text-white/70">
        <p class="text-sm sm:text-base">&copy; {{ new Date().getUTCFullYear() }} Chronos. Terminplanung für Jugendverbände.</p>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({
  layout: 'landingpage'
})
const features = [
  {
    icon: 'pi pi-calendar',
    title: 'Termine organisieren',
    description: 'Erstelle und verwalte alle Team-Termine an einem zentralen Ort. Nie wieder durcheinander.'
  },
  {
    icon: 'pi pi-users',
    title: 'Verfügbarkeit abfragen',
    description: 'Sehe auf einen Blick, wer teilnehmen kann und ob genug Helfer dabei sind.'
  },
  {
    icon: 'pi pi-bell',
    title: 'Push-Benachrichtigungen',
    description: 'Automatische Erinnerungen an wichtige Termine und laufende Abfragen.'
  },
  {
    icon: 'pi pi-check-circle',
    title: 'Einfache Zusagen',
    description: 'Schnell und unkompliziert zu- oder absagen – in Sekunden erledigt.'
  }
];
const scrollToFeatures = () => {
  const element = document.getElementById('features');
  if (element) {
    element.scrollIntoView({ behavior: 'smooth' });
  }
};
import {useAuthStore} from "~/stores/auth";
import type {Appointment} from '~/types'
import {DateTime} from "luxon"

const {fetchUser, authenticated} = useAuthStore()
await fetchUser()
if (authenticated) {
  navigateTo('/agenda');
}

const demoAppointment1 = ref<Appointment>({
  id: 0,
  name: "Sommerfest",
  description: "Sommerfest mit allen Mitgliedern",
  start: DateTime.now().plus({month: 1}).toISODate(),
  end: DateTime.now().plus({month: 1, hour: 3}).toISODate(),
  status: "PLANNED",
  venue: "",
  minimal_attendees: 8,
  participants: [
    { user_id: 1, name: "A", profile_picture_url: "", role: "ATTENDANT", status: "REJECTED", via_group_id: null, via_group_name: null },
    { user_id: 2, name: "B", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 3, name: "C", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 4, name: "D", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 5, name: "E", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 6, name: "F", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 7, name: "G", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 8, name: "H", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 9, name: "I", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 10, name: "J", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 11, name: "K", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null }
  ],
  group_participants: [],
  messages: []
});
const demoAppointment2 = ref<Appointment>({
  id: 1,
  name: "Team-Meeting",
  description: "Wir müssen reflektieren, wie das Sommerfest gelaufen ist",
  start: DateTime.now().plus({month: 2}).toISODate(),
  end: DateTime.now().plus({month: 2, hour: 3}).toISODate(),
  status: "CANCELLED",
  venue: "",
  minimal_attendees: 5,
  participants: [
    { user_id: 1, name: "A", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 2, name: "B", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 3, name: "C", profile_picture_url: "", role: "ATTENDANT", status: "REJECTED", via_group_id: null, via_group_name: null },
    { user_id: 4, name: "D", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 5, name: "E", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null }
  ],
  group_participants: [],
  messages: []
});
const demoAppointment3 = ref<Appointment>({
  id: 2,
  name: "Gruppenstunde",
  description: "",
  start: DateTime.now().plus({month: 2}).toISODate(),
  end: DateTime.now().plus({month: 2}).toISODate(),
  status: "NOT_ENOUGH_ATTENDEES",
  venue: "",
  minimal_attendees: 2,
  participants: [
    { user_id: 1, name: "A", profile_picture_url: "", role: "ATTENDANT", status: "APPROVED", via_group_id: null, via_group_name: null },
    { user_id: 2, name: "B", profile_picture_url: "", role: "ATTENDANT", status: "REJECTED", via_group_id: null, via_group_name: null },
    { user_id: 3, name: "C", profile_picture_url: "", role: "ATTENDANT", status: "REJECTED", via_group_id: null, via_group_name: null },
    { user_id: 4, name: "D", profile_picture_url: "", role: "ATTENDANT", status: "REJECTED", via_group_id: null, via_group_name: null },
    { user_id: 5, name: "E", profile_picture_url: "", role: "ATTENDANT", status: "REJECTED", via_group_id: null, via_group_name: null }
  ],
  group_participants: [],
  messages: []
});
</script>

<style scoped>
.animation-delay-1000 {
  animation-delay: 1s;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.2;
  }
  50% {
    opacity: 0.4;
  }
}

.animate-pulse {
  animation: pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>