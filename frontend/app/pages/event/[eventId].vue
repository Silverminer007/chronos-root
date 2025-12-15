<template>
  <div v-if="eventStore.loading" class="min-h-screen bg-gray-50 dark:bg-neutral-900 flex items-center justify-center">
    <div class="text-center">
      <i class="pi pi-spin pi-spinner text-4xl text-purple-600 dark:text-purple-400 mb-4"></i>
      <p class="text-gray-600 dark:text-gray-400">Event wird geladen...</p>
    </div>
  </div>

  <div v-else-if="error" class="min-h-screen bg-gray-50 dark:bg-neutral-900 flex items-center justify-center">
    <div class="text-center max-w-md mx-auto p-6">
      <i class="pi pi-exclamation-circle text-4xl text-red-600 dark:text-red-400 mb-4"></i>
      <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Event nicht gefunden</h2>
      <p class="text-gray-600 dark:text-gray-400 mb-6">{{ error }}</p>
      <button
          @click="goBack"
          class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600"
      >
        Zurück zur Übersicht
      </button>
    </div>
  </div>

  <div v-else-if="event" class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <!-- Header -->
    <EventHeader :event="event"/>

    <!-- Main Content -->
    <main class="container mx-auto px-4 sm:px-6 py-6 max-w-6xl">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Column: Event Details & Actions -->
        <div class="lg:col-span-2 space-y-6">
          <EventDetailsCard :event="event" class="order-1"/>
          <AttendanceStatusCard :event="event" class="order-2"/>

          <!-- Mobile: Attendees Cards here (order-3 & order-4) -->
          <AttendeesCard :event="event" class="order-3 lg:hidden"/>
          <AttendeesRolesCard :event="event" class="order-4 lg:hidden"/>

          <EventResponsibleActions :event="event" class="order-5"/>
          <MessagesCard :event="event" class="order-6"/>
        </div>

        <!-- Right Column: Attendees & Roles (Desktop only) -->
        <div class="hidden lg:block lg:col-span-1 space-y-6">
          <AttendeesCard :event="event"/>
          <AttendeesRolesCard :event="event"/>
        </div>
      </div>
    </main>
    <Toast/>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted} from 'vue';
import {useRoute} from 'vue-router';
import {useEventsStore} from '~/stores/events';
import AttendeesRolesCard from "~/components/AttendeesRolesCard.vue";

const route = useRoute();
const eventStore = useEventsStore();

const eventId = computed(() => Number(route.params.eventId));
const event = computed(() => eventStore.currentEvent);
const error = ref('');

const goBack = () => {
  navigateTo('/agenda');
};

onMounted(async () => {
  await eventStore.getEventById(eventId.value);
  if (eventStore.error) {
    error.value = 'Event konnte nicht geladen werden';
  } else {
    error.value = '';
  }
});
</script>

<style scoped>
/* Custom scrollbar */
.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: transparent;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.dark .overflow-y-auto::-webkit-scrollbar-thumb {
  background: #475569;
}

.dark .overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>