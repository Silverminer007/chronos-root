<script setup lang="ts">
import type {Event} from '~/types'
import {useDateFormatter} from "~/composables/useDateFormatter";
import EditEventDialog from "~/components/EditEventDialog.vue";

defineProps<{
  event: Event
}>()

const {formatDate, formatTime} = useDateFormatter();

const showEditDialog = ref<boolean>(false);

const getEventStatusLabel = (status: string) => {
  const labels = {
    CANCELLED: 'Abgesagt',
    NOT_ENOUGH_ATTENDEES: 'Zu wenig Teilnehmer',
    DELETED: 'Gelöscht',
    PLANNED: 'Geplant'
  };
  return labels[status] || status;
};

const getEventStatusClass = (status: string) => {
  const classes = {
    CANCELLED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    NOT_ENOUGH_ATTENDEES: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    DELETED: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-400'
  };
  return classes[status] || '';
};
</script>

<template>
  <div
      class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 overflow-hidden">
    <div class="bg-linear-to-r from-purple-600 to-pink-500 dark:from-purple-500 dark:to-pink-400 p-6">
      <div class="flex items-start justify-between gap-4 mb-2">
        <h2 class="text-2xl sm:text-3xl font-bold text-white">{{ event.name }}</h2>
        <span
            v-if="event.status !== 'PLANNED'"
            class="px-3 py-1 rounded-full text-sm font-medium shrink-0"
            :class="getEventStatusClass(event.status)"
        >
                  {{ getEventStatusLabel(event.status) }}
                </span>
        <button
            @click="showEditDialog = true"
            class="w-10 h-10 flex items-center justify-center rounded-lg bg-white/20 hover:bg-white/30 text-white transition-colors"
            title="Termin bearbeiten"
        >
          <i class="pi pi-pencil"></i>
        </button>
      </div>
      <div class="flex flex-wrap gap-4 text-white/90 text-sm">
        <div class="flex items-center gap-2">
          <i class="pi pi-calendar"></i>
          <span>{{ formatDate(event.start) }}</span>
        </div>
        <div class="flex items-center gap-2">
          <i class="pi pi-clock"></i>
          <span>{{ formatTime(event.start) }} - {{ formatTime(event.end) }}</span>
        </div>
        <div v-if="event.venue" class="flex items-center gap-2">
          <i class="pi pi-map-marker"></i>
          <span>{{ event.venue }}</span>
        </div>
      </div>
    </div>

    <div class="p-6 space-y-4">
      <div v-if="event.description">
        <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-2">Beschreibung</h3>
        <p class="text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ event.description }}</p>
      </div>

      <div class="flex items-center gap-2 text-sm">
        <i class="pi pi-users text-gray-400"></i>
        <span class="text-gray-600 dark:text-gray-400">
                  Mindestens {{ event.minimal_attendees }} Teilnehmer erforderlich
                </span>
      </div>
    </div>
  </div>
  <EditEventDialog
      v-model:visible="showEditDialog"
      :event="event"
  />
</template>

<style scoped>

</style>