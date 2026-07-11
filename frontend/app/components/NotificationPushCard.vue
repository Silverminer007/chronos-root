<template>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
    <div class="flex items-center gap-4">
      <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center shrink-0">
        <Icon name="lucide:bell" class=" text-purple-600 dark:text-purple-400 text-xl" />
      </div>

      <div class="flex-1">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white">
          Push-Benachrichtigungen
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          Erhalte Benachrichtigungen auf diesem Gerät
        </p>
        <p class="text-xs mt-1" :class="statusColor">
          {{ statusText }}
        </p>
      </div>

      <button
          :disabled="permission === 'denied'"
          class="relative w-14 h-8 rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
          :class="enabled ? 'bg-purple-600' : 'bg-gray-300 dark:bg-neutral-600'"
          @click="emit('toggle')"
      >
        <span
            class="absolute top-1 left-1 w-6 h-6 bg-white rounded-full shadow transition-transform duration-200"
            :class="enabled ? 'translate-x-6' : 'translate-x-0'"
        ></span>
      </button>
    </div>

    <div
v-if="permission === 'denied'"
         class="mt-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
      <p class="text-sm text-yellow-800 dark:text-yellow-200">
        <Icon name="lucide:info" class=" mr-2" />
        Push-Benachrichtigungen wurden im Browser blockiert.
        Bitte ändere die Berechtigung in den Browsereinstellungen.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  enabled: boolean;
  permission: NotificationPermission;
}>();

const emit = defineEmits<{
  toggle: [];
}>();

const statusText = computed(() => {
  if (props.permission === 'denied') {
    return 'Im Browser blockiert';
  }
  return props.enabled ? 'Aktiviert' : 'Deaktiviert';
});

const statusColor = computed(() => {
  if (props.permission === 'denied') return 'text-red-500';
  return props.enabled ? 'text-green-500' : 'text-gray-500';
});
</script>
