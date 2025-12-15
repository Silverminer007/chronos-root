<script setup lang="ts">
import type {Event} from "~/types";
import {computed, ref} from "vue";
import {useAuthStore} from "~/stores/auth";
import {useEventsStore} from "~/stores/events";
import {useDateFormatter} from "~/composables/useDateFormatter";
import {useToast} from "primevue/usetoast";
import Toast from "primevue/toast";

const authStore = useAuthStore();
const eventStore = useEventsStore();
const {formatDateTime} = useDateFormatter();

const toast = useToast();

const {event} = defineProps<{
  event: Event;
}>();

const showMessageDialog = ref(false);

const canSendMessage = computed(() => {
  if (!event || !authStore.user?.id) return false;
  return event.userAttendees?.some(
      ua => ua.user.id === authStore.user?.id &&
          (ua.role === 'ATTENDANT' || ua.role === 'RESPONSIBLE')
  );
});

const sortedMessages = computed(() => {
  if (!event) return [];
  return [...event.messages].sort((a, b) =>
      new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );
});


const handleSendMessage = async (data: { subject: string; message: string }) => {
  if (!event) return;

  try {
    await eventStore.sendMessage(event.id, data.subject, data.message, authStore.user);
    toast.add({
      severity: 'success',
      summary: 'Nachricht versendet',
      life: 3000
    });
    showMessageDialog.value = false;
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler beim Senden',
      detail: 'Bitte kontaktiere den Entwickler der App',
      life: 3000
    });
  }
};
</script>

<template>
  <Toast/>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
    <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white">Nachrichten</h3>
        <button
            v-if="canSendMessage"
            @click="showMessageDialog = true"
            class="px-4 py-2 rounded-lg font-medium text-white transition-all bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 flex items-center justify-center gap-2"
        >
          <i class="pi pi-send"></i>
          <span>Nachricht senden</span>
        </button>
      </div>
    </div>

    <div class="divide-y divide-gray-200 dark:divide-neutral-700">
      <div
          v-for="message in sortedMessages"
          :key="message.id"
          class="p-6 hover:bg-gray-50 dark:hover:bg-neutral-700/50 transition-colors"
      >
        <div class="flex gap-4">
          <div
              class="w-10 h-10 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
            <i class="pi pi-user text-purple-600 dark:text-purple-400"></i>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
              <div>
                <p class="font-semibold text-gray-900 dark:text-white">{{ message.sender_name }}</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">{{ formatDateTime(message.timestamp) }}</p>
              </div>
            </div>
            <h4 class="font-medium text-gray-900 dark:text-white mb-2">{{ message.title }}</h4>
            <p class="text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ message.body }}</p>
          </div>
        </div>
      </div>

      <div v-if="event.messages.length === 0" class="p-12 text-center">
        <i class="pi pi-inbox text-4xl text-gray-300 dark:text-gray-600 mb-4"></i>
        <p class="text-gray-500 dark:text-gray-400">Noch keine Nachrichten</p>
      </div>
    </div>
  </div>
  <MessageDialog
      :visible="showMessageDialog"
      :event-title="event.name"
      :recipient-count="event.attendances.length"
      @close="showMessageDialog = false"
      @send="handleSendMessage"
  />
</template>

<style scoped>

</style>