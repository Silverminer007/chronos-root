<script setup lang="ts">
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

let deferredPrompt: BeforeInstallPromptEvent | null = null;

const visible = ref(false);

onMounted(() => {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e as BeforeInstallPromptEvent;
    visible.value = true;
  });
});

async function install() {
  if (!deferredPrompt) return;

  deferredPrompt.prompt();

  await deferredPrompt.userChoice;
  deferredPrompt = null;
  visible.value = false;
}

function close() {
  visible.value = false;
}
</script>
<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div class="w-full max-w-md bg-white dark:bg-neutral-800 rounded-xl shadow-2xl p-6 sm:p-8 transform transition-all">
      <div class="flex items-center gap-3 mb-4">
        <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center flex-shrink-0">
          <Icon name="lucide:download" class=" text-purple-600 dark:text-purple-400 text-xl" />
        </div>
        <h2 class="text-xl font-bold text-gray-900 dark:text-white">App installieren</h2>
      </div>

      <p class="text-sm text-gray-600 dark:text-gray-300 mb-6">
        Installiere Chronos für einen schnelleren Zugriff und ein besseres Benutzererlebnis. Du kannst die App direkt vom Homescreen öffnen.
      </p>

      <div class="flex flex-col sm:flex-row justify-end gap-3">
        <button
            class="px-4 py-2.5 rounded-lg font-medium transition-all border-2 border-purple-600 text-purple-600 hover:bg-purple-50 dark:border-purple-400 dark:text-purple-400 dark:hover:bg-purple-900/20"
            @click="close"
        >
          Später
        </button>

        <button
            class="px-4 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400"
            @click="install"
        >
          Jetzt installieren
        </button>
      </div>
    </div>
  </div>
</template>