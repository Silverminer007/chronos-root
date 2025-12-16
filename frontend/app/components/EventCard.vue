<script setup lang="ts">
import type {Event} from '~/types';
import {useDateFormatter} from '~/composables/useDateFormatter';
import {useAuthStore} from "~/stores/auth";

const {event} = defineProps<{
  event: Event
}>()

const eventsStore = useEventsStore();
const {formatTimeRange, formatDate} = useDateFormatter();
const {user} = useAuthStore()

const messageDialog = ref<boolean>(false);

async function sendMessage(messageSubject: string, messageBody: string) {
  await eventsStore.sendMessage(event.id, messageSubject, messageBody, user);
  messageDialog.value = false;
}
</script>

<template>
  <Toast />
  <MessageDialog
      :visible="messageDialog"
      :eventTitle="`${event.name} ${formatDate(event.start)}`"
      :recipientCount="event.attendances.length"
      @close="messageDialog = false"
      @send="sendMessage($event.subject, $event.message)"
  />

  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 overflow-hidden hover:shadow-lg transition-all duration-300">
    <!-- Status Banner -->
    <div v-if="event.status === 'CANCELLED'" class="bg-red-600 px-4 py-2 flex items-center gap-2">
      <i class="pi pi-exclamation-triangle text-white"></i>
      <span class="text-white font-medium text-sm">Event abgesagt</span>
    </div>
    <div v-else-if="event.status === 'NOT_ENOUGH_ATTENDEES'" class="bg-yellow-500 px-4 py-2 flex items-center gap-2">
      <i class="pi pi-exclamation-triangle text-white"></i>
      <span class="text-white font-medium text-sm">Zu wenig Teilnehmende</span>
    </div>

    <!-- Card Content -->
    <div class="p-6">
      <!-- Header -->
      <NuxtLink :to="`/event/${event.id}`" class="block group">
        <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4 mb-4">
          <div class="flex-1 min-w-0">
            <h3 class="text-xl font-bold text-gray-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400 transition-colors mb-1">
              {{ event.name }}
            </h3>
            <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
              <i class="pi pi-calendar text-xs"></i>
              <span>{{ formatTimeRange(event.start, event.end) }}</span>
            </div>
            <div v-if="event.venue" class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400 mt-1">
              <i class="pi pi-map-marker text-xs"></i>
              <span>{{ event.venue }}</span>
            </div>
          </div>

          <!-- Attendance Count & Avatars -->
          <div class="flex items-center gap-3 shrink-0">
            <div v-if="event.minimal_attendees" class="text-right">
              <div class="text-2xl font-bold text-gray-900 dark:text-white">
                {{ eventsStore.getApprovedAttendances(event).length }}<span class="text-gray-400">/{{ event.minimal_attendees }}</span>
              </div>
              <div class="text-xs text-gray-500 dark:text-gray-400">Zusagen</div>
            </div>

            <!-- Avatar Stack -->
            <div v-if="eventsStore.getApprovedAttendances(event).length > 0" class="flex -space-x-2">
              <div
                  v-for="(attendance, index) in eventsStore.getApprovedAttendances(event).slice(0, 4)"
                  :key="attendance.id"
                  v-tooltip.top="attendance.user_name"
                  class="w-8 h-8 sm:w-10 sm:h-10 rounded-full border-2 border-white dark:border-neutral-800 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center text-purple-600 dark:text-purple-400 font-semibold text-sm transition-transform hover:scale-110 hover:z-10"
                  :style="{ zIndex: 10 - index }"
              >
                {{ attendance?.user_name?.charAt(0) }}
              </div>
              <div
                  v-if="eventsStore.getApprovedAttendances(event).length > 4"
                  class="w-8 h-8 sm:w-10 sm:h-10 rounded-full border-2 border-white dark:border-neutral-800 bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-gray-600 dark:text-gray-400 font-semibold text-xs"
              >
                +{{ eventsStore.getApprovedAttendances(event).length - 4 }}
              </div>
            </div>
          </div>
        </div>
      </NuxtLink>

      <!-- Description -->
      <p v-if="event.description" class="text-gray-600 dark:text-gray-400 text-sm mb-4 line-clamp-2">
        {{ event.description }}
      </p>

      <!-- Action Buttons -->
      <div class="flex flex-wrap gap-2">
        <button
            :disabled="eventsStore.hasApproved(event)"
            @click.stop="eventsStore.updateAttendanceStatus(event.id, 'APPROVED', user?.id)"
            class="flex-1 sm:flex-initial px-4 py-2.5 rounded-lg font-medium transition-all flex items-center justify-center gap-2 disabled:cursor-not-allowed"
            :class="eventsStore.hasApproved(event)
              ? 'bg-green-600 text-white'
              : 'border-2 border-green-600 text-green-600 hover:bg-green-50 dark:border-green-500 dark:text-green-500 dark:hover:bg-green-900/20'"
        >
          <i class="pi pi-check text-sm"></i>
          <span class="hidden sm:inline">Zusagen</span>
        </button>

        <button
            @click="messageDialog = true"
            class="flex-1 sm:flex-initial px-4 py-2.5 rounded-lg font-medium transition-all border-2 border-purple-600 dark:border-purple-400 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 flex items-center justify-center gap-2"
        >
          <i class="pi pi-send text-sm"></i>
          <span class="hidden sm:inline">Nachricht</span>
        </button>

        <button
            :disabled="eventsStore.hasRejected(event)"
            @click.stop="eventsStore.updateAttendanceStatus(event.id, 'REJECTED', user?.id)"
            class="flex-1 sm:flex-initial px-4 py-2.5 rounded-lg font-medium transition-all flex items-center justify-center gap-2 disabled:cursor-not-allowed"
            :class="eventsStore.hasRejected(event)
              ? 'bg-red-600 text-white'
              : 'border-2 border-red-600 text-red-600 hover:bg-red-50 dark:border-red-500 dark:text-red-500 dark:hover:bg-red-900/20'"
        >
          <i class="pi pi-times text-sm"></i>
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