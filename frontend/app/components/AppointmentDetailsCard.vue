<script setup lang="ts">
import type {Appointment} from '~/types'
import {useDateFormatter} from "~/composables/useDateFormatter";
import EditAppointmentDialog from "~/components/EditAppointmentDialog.vue";

defineProps<{
  appointment: Appointment
}>()

const {formatDate, formatTime} = useDateFormatter();

const showEditDialog = ref<boolean>(false);

const getStatusLabel = (status: string) => {
  const labels = {
    CANCELLED: 'Abgesagt',
    NOT_ENOUGH_ATTENDEES: 'Zu wenig Teilnehmer',
    DELETED: 'Gelöscht',
    PLANNED: 'Geplant'
  };
  return labels[status] || status;
};

const getStatusClass = (status: string) => {
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
        <h2 class="text-2xl sm:text-3xl font-bold text-white">{{ appointment.name }}</h2>
        <span
            v-if="appointment.status !== 'PLANNED'"
            class="px-3 py-1 rounded-full text-sm font-medium shrink-0"
            :class="getStatusClass(appointment.status)"
        >
                  {{ getStatusLabel(appointment.status) }}
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
          <span>{{ formatDate(appointment.start) }}</span>
        </div>
        <div class="flex items-center gap-2">
          <i class="pi pi-clock"></i>
          <span>{{ formatTime(appointment.start) }} - {{ formatTime(appointment.end) }}</span>
        </div>
        <div v-if="appointment.venue" class="flex items-center gap-2">
          <i class="pi pi-map-marker"></i>
          <span>{{ appointment.venue }}</span>
        </div>
      </div>
    </div>

    <div class="p-6 space-y-4">
      <div v-if="appointment.description">
        <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-2">Beschreibung</h3>
        <p class="text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ appointment.description }}</p>
      </div>

      <div class="flex items-center gap-2 text-sm">
        <i class="pi pi-users text-gray-400"></i>
        <span class="text-gray-600 dark:text-gray-400">
                  Mindestens {{ appointment.minimal_attendees }} Teilnehmer erforderlich
                </span>
      </div>
    </div>
  </div>
  <EditAppointmentDialog
      v-model:visible="showEditDialog"
      :appointment="appointment"
  />
</template>

<style scoped>

</style>