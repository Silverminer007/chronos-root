<script setup lang="ts">
import type {User} from '~/types';

const props = defineProps<{
  member: User;
  canRemove: boolean;
}>();

const emit = defineEmits<{
  remove: [userId: number];
}>();

const fullName = computed(() => `${props.member.first_name} ${props.member.last_name}`);
const initials = computed(() => `${props.member.first_name?.charAt(0) || ''}${props.member.last_name?.charAt(0) || ''}`);
</script>

<template>
  <div class="flex items-center gap-3 p-3 bg-white dark:bg-neutral-800 rounded-lg border border-gray-200 dark:border-neutral-700">
    <div
        class="w-10 h-10 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
      <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
        {{ initials }}
      </span>
    </div>

    <div class="flex-1 min-w-0">
      <p class="font-medium text-gray-900 dark:text-white truncate">{{ fullName }}</p>
    </div>

    <button
        v-if="canRemove"
        @click="emit('remove', member.id)"
        class="p-2 text-gray-400 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors shrink-0"
        title="Mitglied entfernen"
    >
      <i class="pi pi-times text-sm"></i>
    </button>
  </div>
</template>
