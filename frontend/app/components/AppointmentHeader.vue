<script setup lang="ts">
import type {Appointment} from '~/types';

defineProps<{
  appointment: Appointment;
}>();
const router = useRouter();
const goBack = () => {
  if (router.options.history.state.back) {
    router.back();
  } else {
    navigateTo('/agenda');
  }
};
</script>

<template>
  <header class="bg-white dark:bg-neutral-800 border-b border-gray-200 dark:border-neutral-700 sticky top-0 z-40">
    <div class="container mx-auto px-4 sm:px-6 py-4">
      <div class="flex items-center gap-4">
        <button
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
            @click="goBack"
        >
          <Icon name="lucide:arrow-left" class=" text-gray-600 dark:text-gray-400" />
        </button>
        <div class="flex-1 min-w-0">
          <h1 class="text-xl sm:text-2xl font-bold text-gray-900 dark:text-white truncate">
            {{ appointment.name }}
          </h1>
        </div>
      </div>
    </div>
  </header>
</template>