<script setup lang="ts">
import type {Appointment} from '~/types';
import {useDateFormatter} from '~/composables/useDateFormatter';
import {useAuthStore} from "~/stores/auth";
import {useAppointmentsStore} from "~/stores/appointments";
import {useToast} from "primevue/usetoast";
import MessageDialog from "./MessageDialog.vue";

const {appointment} = defineProps<{
  appointment: Appointment
}>()

const toast = useToast()
const appointmentStore = useAppointmentsStore();
const {formatTimeRange, formatDate} = useDateFormatter();
const {user} = useAuthStore()

const messageDialog = ref<boolean>(false);

// Get approved participants
const approvedParticipants = computed(() =>
    appointment.participants?.filter(p => p.status === 'APPROVED') || []
);

// Check if current user has approved
const hasApproved = computed(() =>
    appointment.participants?.some(p => p.user_id === user?.id && p.status === 'APPROVED') || false
);

// Check if current user has rejected
const hasRejected = computed(() =>
    appointment.participants?.some(p => p.user_id === user?.id && p.status === 'REJECTED') || false
);

async function handleApprove() {
  if (!user) return;
  try {
    await appointmentStore.approveAppointment(appointment.id);
    toast.add({
      severity: 'success',
      summary: 'Zusage bestätigt',
      life: 3000
    });
  } catch (err) {
    console.error(err);
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Teilnahmestatus konnte nicht aktualisiert werden',
      life: 3000
    });
  }
}

async function handleReject() {
  if (!user) return;
  try {
    await appointmentStore.rejectAppointment(appointment.id);
    toast.add({
      severity: 'success',
      summary: 'Absage registriert',
      life: 3000
    });
  } catch (err) {
    console.error(err);
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Teilnahmestatus konnte nicht aktualisiert werden',
      life: 3000
    });
  }
}

async function sendMessage(messageBody: string) {
  if (!user) {
    return;
  }

  messageDialog.value = false;
  try {
    await appointmentStore.sendMessage(appointment.id, messageBody);
    toast.add({
      severity: 'success',
      summary: 'Nachricht versendet',
      life: 3000
    });
    messageDialog.value = false;
  } catch (err) {
    console.error(err);
    toast.add({
      severity: 'error',
      summary: 'Fehler beim Senden',
      detail: 'Bitte kontaktiere den Entwickler der App',
      life: 3000
    });
  }
}
</script>

<template>
  <MessageDialog
      :visible="messageDialog"
      :appointment-title="`${appointment.name} ${formatDate(appointment.start)}`"
      :recipient-count="appointment.participants?.length || 0"
      @close="messageDialog = false"
      @send="sendMessage($event.message)"
  />

  <div
      class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 overflow-hidden hover:shadow-lg transition-all duration-300">
    <!-- Status Banner -->
    <div v-if="appointment.status === 'CANCELLED'" class="bg-red-600 px-4 py-2 flex items-center gap-2">
      <Icon name="lucide:triangle-alert" class=" text-white" />
      <span class="text-white font-medium text-sm">Termin abgesagt</span>
    </div>
    <div v-else-if="appointment.status === 'NOT_ENOUGH_ATTENDEES'" class="bg-yellow-500 px-4 py-2 flex items-center gap-2">
      <Icon name="lucide:triangle-alert" class=" text-white" />
      <span class="text-white font-medium text-sm">Zu wenig Teilnehmende</span>
    </div>

    <!-- Card Content -->
    <div class="p-6">
      <!-- Header -->
      <NuxtLink :to="`/appointment/${appointment.id}`" class="block group">
        <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4 mb-4">
          <div class="flex-1 min-w-0">
            <h3 class="text-xl font-bold text-gray-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400 transition-colors mb-1">
              {{ appointment.name }}
            </h3>
            <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
              <Icon name="lucide:calendar" class=" text-xs" />
              <span>{{ formatTimeRange(appointment.start, appointment.end) }}</span>
            </div>
            <div v-if="appointment.venue" class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 mt-1">
              <Icon name="lucide:map-pin" class=" text-xs" />
              <span>{{ appointment.venue }}</span>
            </div>
          </div>

          <!-- Participation Count & Avatars -->
          <div class="flex items-center gap-3 shrink-0">
            <div v-if="appointment.minimal_attendees" class="text-right">
              <div class="text-2xl font-bold text-gray-900 dark:text-white">
                {{ approvedParticipants.length }}<span
                  class="text-gray-400">/{{ appointment.minimal_attendees }}</span>
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400">Zusagen</div>
            </div>

            <!-- Avatar Stack -->
            <div v-if="approvedParticipants.length > 0" class="flex -space-x-2">
              <div
                  v-for="(participant, index) in approvedParticipants.slice(0, 4)"
                  :key="participant.user_id"
                  v-tooltip.top="participant.name"
                  class="w-8 h-8 sm:w-10 sm:h-10 rounded-full border-2 border-white dark:border-neutral-800 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center text-purple-600 dark:text-purple-400 font-semibold text-sm transition-transform hover:scale-110 hover:z-10"
                  :style="{ zIndex: 10 - index }"
              >
                {{ participant.name?.charAt(0) || '?' }}
              </div>
              <div
                  v-if="approvedParticipants.length > 4"
                  class="w-8 h-8 sm:w-10 sm:h-10 rounded-full border-2 border-white dark:border-neutral-800 bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-gray-600 dark:text-gray-400 font-semibold text-xs"
              >
                +{{ approvedParticipants.length - 4 }}
              </div>
            </div>
          </div>
        </div>
      </NuxtLink>

      <!-- Description -->
      <p v-if="appointment.description" class="text-gray-600 dark:text-gray-400 text-sm mb-4 line-clamp-2">
        {{ appointment.description }}
      </p>

      <!-- Action Buttons -->
      <div class="flex flex-wrap gap-2">
        <button
            :disabled="hasApproved"
            class="flex-1 sm:flex-initial px-4 py-2.5 rounded-lg font-medium transition-all flex items-center justify-center gap-2 disabled:cursor-not-allowed"
            :class="hasApproved
              ? 'bg-green-600 text-white'
              : 'border-2 border-green-600 text-green-600 hover:bg-green-50 dark:border-green-500 dark:text-green-500 dark:hover:bg-green-900/20'"
            @click.stop="handleApprove"
        >
          <Icon name="lucide:check" class=" text-sm" />
          <span class="hidden sm:inline">Zusagen</span>
        </button>

        <button
            class="flex-1 sm:flex-initial px-4 py-2.5 rounded-lg font-medium transition-all border-2 border-purple-600 dark:border-purple-400 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 flex items-center justify-center gap-2"
            @click="messageDialog = true"
        >
          <Icon name="lucide:send" class=" text-sm" />
          <span class="hidden sm:inline">Nachricht</span>
        </button>

        <button
            :disabled="hasRejected"
            class="flex-1 sm:flex-initial px-4 py-2.5 rounded-lg font-medium transition-all flex items-center justify-center gap-2 disabled:cursor-not-allowed"
            :class="hasRejected
              ? 'bg-red-600 text-white'
              : 'border-2 border-red-600 text-red-600 hover:bg-red-50 dark:border-red-500 dark:text-red-500 dark:hover:bg-red-900/20'"
            @click.stop="handleReject"
        >
          <Icon name="lucide:x" class=" text-sm" />
          <span class="hidden sm:inline">Absagen</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Line clamp for description */
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Disabled button state */
button:disabled {
  opacity: 1 !important;
  filter: none !important;
}
</style>
