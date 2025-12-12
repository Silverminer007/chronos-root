<script setup lang="ts">
import { ref, onMounted } from 'vue';

let deferredPrompt: any = null;

const visible = ref(false);

onMounted(() => {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    visible.value = true;
  });
});

async function install() {
  if (!deferredPrompt) return;

  deferredPrompt.prompt();

  const result = await deferredPrompt.userChoice;
  deferredPrompt = null;
  visible.value = false;

  console.log('User install choice:', result);
}

function close() {
  visible.value = false;
}
</script>

<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50">
    <div class="w-80 bg-white dark:bg-neutral-800 rounded-lg shadow-xl p-6">
      <h2 class="text-lg font-bold mb-3">App installieren</h2>
      <p class="text-sm mb-5">
        Installiere diese Anwendung für einen schnelleren Zugriff und ein besseres Benutzererlebnis.
      </p>

      <div class="flex justify-end gap-3">
        <button
            class="px-3 py-2 rounded bg-neutral-300 hover:bg-neutral-400 dark:bg-neutral-700 dark:hover:bg-neutral-600"
            @click="close"
        >
          Später
        </button>

        <button
            class="px-3 py-2 rounded bg-blue-600 text-white hover:bg-blue-700"
            @click="install"
        >
          Installieren
        </button>
      </div>
    </div>
  </div>
</template>