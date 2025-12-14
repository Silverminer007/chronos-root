<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div class="w-full max-w-2xl bg-white dark:bg-neutral-800 rounded-xl shadow-2xl transform transition-all">
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-neutral-700">
        <div class="flex items-center gap-3">
          <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center shrink-0">
            <i class="pi pi-send text-purple-600 dark:text-purple-400 text-xl"></i>
          </div>
          <div>
            <h2 class="text-xl font-bold text-gray-900 dark:text-white">Nachricht senden</h2>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ eventTitle }}</p>
          </div>
        </div>
        <button
            @click="close"
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
            aria-label="Schließen"
        >
          <i class="pi pi-times text-gray-500 dark:text-gray-400 text-lg"></i>
        </button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-5">
        <!-- Empfänger Info -->
        <div class="bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 rounded-lg p-4">
          <div class="flex items-start gap-3">
            <i class="pi pi-info-circle text-purple-600 dark:text-purple-400 text-lg mt-0.5"></i>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-purple-900 dark:text-purple-200 mb-1">
                Empfänger
              </p>
              <p class="text-sm text-purple-700 dark:text-purple-300">
                Alle Teilnehmer dieses Termins ({{ recipientCount }} Personen)
              </p>
            </div>
          </div>
        </div>

        <!-- Betreff -->
        <div>
          <label for="subject" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Betreff
          </label>
          <input
              id="subject"
              v-model="subject"
              type="text"
              placeholder="z.B. Wichtige Information zum Termin"
              class="w-full px-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
          />
        </div>

        <!-- Nachricht -->
        <div>
          <label for="message" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Nachricht
          </label>
          <textarea
              id="message"
              v-model="message"
              rows="6"
              placeholder="Schreibe hier deine Nachricht an alle Teilnehmer..."
              class="w-full px-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all resize-none"
          ></textarea>
          <div class="flex justify-between items-center mt-2">
            <span class="text-xs text-gray-500 dark:text-gray-400">
              {{ message.length }} / 500 Zeichen
            </span>
            <span v-if="message.length > 500" class="text-xs text-red-600 dark:text-red-400">
              Maximale Länge überschritten
            </span>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex flex-col-reverse sm:flex-row justify-end gap-3 p-6 border-t border-gray-200 dark:border-neutral-700">
        <button
            class="px-5 py-2.5 rounded-lg font-medium transition-all border-2 border-gray-300 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-neutral-700"
            @click="close"
        >
          Abbrechen
        </button>

        <button
            class="px-5 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 dark:hover:from-purple-600 dark:hover:to-pink-500 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            :disabled="!isValid || sending"
            @click="send"
        >
          <i v-if="sending" class="pi pi-spin pi-spinner"></i>
          <i v-else class="pi pi-send"></i>
          <span>{{ sending ? 'Wird gesendet...' : 'Nachricht senden' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

interface Props {
  visible: boolean;
  eventTitle: string;
  recipientCount: number;
}

defineProps<Props>();

const emit = defineEmits<{
  close: [];
  send: [data: { subject: string; message: string; }];
}>();

const subject = ref('');
const message = ref('');
const sending = ref(false);

const isValid = computed(() => {
  return subject.value.trim().length > 0 &&
      message.value.trim().length >= 0 &&
      message.value.length <= 500;
});

const close = () => {
  emit('close');
};

const send = async () => {
  if (!isValid.value || sending.value) return;

  sending.value = true;

  emit('send', {
    subject: subject.value,
    message: message.value
  });

  // Reset form
  subject.value = '';
  message.value = '';
  sending.value = false;
};
</script>

<style scoped>
/* Custom scrollbar for textarea */
textarea::-webkit-scrollbar {
  width: 8px;
}

textarea::-webkit-scrollbar-track {
  background: transparent;
}

textarea::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

textarea::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.dark textarea::-webkit-scrollbar-thumb {
  background: #475569;
}

.dark textarea::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>