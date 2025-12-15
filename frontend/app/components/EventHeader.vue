<script setup lang="ts">
import type {Event} from '~/types';

defineProps<{
  event: Event;
}>();
const goBack = () => {
  navigateTo('/agenda');
};
</script>

<template>
  <header class="bg-white dark:bg-neutral-800 border-b border-gray-200 dark:border-neutral-700 sticky top-0 z-40">
    <div class="container mx-auto px-4 sm:px-6 py-4">
      <div class="flex items-center gap-4">
        <button
            @click="goBack"
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
        >
          <i class="pi pi-arrow-left text-gray-600 dark:text-gray-400"></i>
        </button>
        <div class="flex-1 min-w-0">
          <h1 class="text-xl sm:text-2xl font-bold text-gray-900 dark:text-white truncate">
            {{ event.name }}
          </h1>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>

</style>