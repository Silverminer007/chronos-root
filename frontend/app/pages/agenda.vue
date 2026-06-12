<script setup lang="ts">
import {useAuthStore} from "~/stores/auth";
import {useAppointmentsStore} from "~/stores/appointments";

const {fetchUser} = useAuthStore()
await fetchUser()
const appointmentsStore = useAppointmentsStore()

// Get request headers for SSR - $fetch in Pinia stores doesn't have request context
const headers = import.meta.server ? useRequestHeaders(['cookie']) : undefined
await useAsyncData('appointments', () => appointmentsStore.loadInitialAppointments({headers}))

const showCreateDialog = ref(false);
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <!-- Header -->
    <SearchHeader/>

    <!-- Body -->
    <div class="container mx-auto px-4 sm:px-6  pb-24">
      <div class="max-w-4xl mx-auto space-y-6">
        <!-- Appointment Cards -->
        <AppointmentCard
            v-for="appointment in appointmentsStore.appointments" :key="appointment.id"
            :appointment="appointment"/>

        <!-- Skeletons -->
        <div v-if="appointmentsStore.loading">
          <AppointmentCardSkeleton v-for="i in [1, 2, 3, 4, 5]" :key="i"/>
        </div>

        <!-- Error State -->
        <div
            v-if="appointmentsStore.error"
            class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <Icon name="lucide:triangle-alert" class=" text-red-600 dark:text-red-400 text-xl"/>
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ appointmentsStore.error }}</p>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div
            v-if="!appointmentsStore.loading && appointmentsStore.appointments.length === 0 && !appointmentsStore.error"
            class="text-center py-16 px-6">
          <div
              class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
            <Icon name="lucide:calendar" class=" text-3xl text-purple-600 dark:text-purple-400"/>
          </div>
          <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Keine Termine vorhanden</h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6">Erstelle deinen ersten Termin oder warte auf eine
            Einladung.</p>
        </div>

        <!-- Load More Button -->
        <div v-if="!appointmentsStore.loading && appointmentsStore.hasMore" class="flex justify-center pt-4">
          <button
              class="px-6 py-3 rounded-lg font-medium text-purple-600 dark:text-purple-400 border-2 border-purple-600 dark:border-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 transition-all flex items-center gap-2"
              @click="appointmentsStore.fetchAppointments"
          >
            <Icon name="lucide:refresh-cw"/>
            <span>Mehr laden</span>
          </button>
        </div>
      </div>
    </div>

    <!-- FAB - Create Appointment -->
    <button
        class="fixed bottom-6 right-6 w-14 h-14 sm:w-16 sm:h-16 rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all transform hover:scale-110 z-50"
        @click="showCreateDialog = true"
    >
      <Icon name="lucide:plus" class=" text-xl sm:text-2xl"/>
    </button>

    <!-- Create Appointment Dialog -->
    <CreateAppointmentDialog v-model:visible="showCreateDialog"/>

    <Toast/>
  </div>
</template>
