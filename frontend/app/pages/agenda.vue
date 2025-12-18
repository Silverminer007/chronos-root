<script setup lang="ts">
import {useAuthStore} from "~/stores/auth";

const {fetchUser} = useAuthStore()
await fetchUser()
const eventStore = useEventsStore()
callOnce('events', () => eventStore.loadInitialEvents())

const showCreateDialog = ref(false);
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <!-- Header -->
    <SearchHeader/>

    <!-- Body -->
    <div class="container mx-auto px-4 sm:px-6  pb-24">
      <div class="max-w-4xl mx-auto space-y-6">
        <!-- Event Cards -->
        <EventCard v-for="event in eventStore.events" :key="event.id" :event="event"/>

        <!-- Skeletons -->
        <EventCardSkeleton v-if="eventStore.loading" v-for="i in [1, 2, 3, 4, 5]" :key="i"/>

        <!-- Error State -->
        <div v-if="eventStore.error"
             class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <i class="pi pi-exclamation-triangle text-red-600 dark:text-red-400 text-xl"></i>
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ eventStore.error }}</p>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="!eventStore.loading && eventStore.events.length === 0 && !eventStore.error"
             class="text-center py-16 px-6">
          <div
              class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
            <i class="pi pi-calendar text-3xl text-purple-600 dark:text-purple-400"></i>
          </div>
          <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Keine Termine vorhanden</h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6">Erstelle deinen ersten Termin oder warte auf eine
            Einladung.</p>
        </div>

        <!-- Load More Button -->
        <div v-if="!eventStore.loading && eventStore.events.length > 0" class="flex justify-center pt-4">
          <button
              @click="eventStore.fetchEvents"
              class="px-6 py-3 rounded-lg font-medium text-purple-600 dark:text-purple-400 border-2 border-purple-600 dark:border-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 transition-all flex items-center gap-2"
          >
            <i class="pi pi-refresh"></i>
            <span>Mehr laden</span>
          </button>
        </div>
      </div>
    </div>

    <!-- FAB - Create Event -->
    <button
        @click="showCreateDialog = true"
        class="fixed bottom-6 right-6 w-14 h-14 sm:w-16 sm:h-16 rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all transform hover:scale-110 z-50"
    >
      <i class="pi pi-plus text-xl sm:text-2xl"></i>
    </button>

    <!-- Create Event Dialog -->
    <CreateEventDialog v-model:visible="showCreateDialog"/>

    <Toast/>
  </div>
</template>