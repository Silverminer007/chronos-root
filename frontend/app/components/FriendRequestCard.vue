<script setup lang="ts">
import type {FriendshipRequest} from '~/types';
import {useDateFormatter} from "~/composables/useDateFormatter";

const props = defineProps<{
  request: FriendshipRequest;
  type: 'incoming' | 'outgoing';
}>();

const emit = defineEmits<{
  accept: [requestId: number];
  decline: [requestId: number];
  cancel: [requestId: number];
}>();

const {formatDateTime} = useDateFormatter();

const loading = ref(false);

const displayName = props.request.userName;

const formattedDate = computed(() => {
  if (!props.request.createdAt) return '';
  return formatDateTime(props.request.createdAt);
});

const handleAccept = async () => {
  loading.value = true;
  emit('accept', props.request.requestId);
};

const handleDecline = async () => {
  loading.value = true;
  emit('decline', props.request.requestId);
};

const handleCancel = async () => {
  loading.value = true;
  emit('cancel', props.request.requestId);
};
</script>

<template>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-4">
    <div class="flex items-center gap-4">
      <div
          class="w-12 h-12 rounded-full flex items-center justify-center shrink-0"
          :class="type === 'incoming'
            ? 'bg-linear-to-br from-green-100 to-emerald-100 dark:from-green-900/30 dark:to-emerald-900/30'
            : 'bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30'"
      >
        <span
            class="font-semibold text-lg"
            :class="type === 'incoming' ? 'text-green-600 dark:text-green-400' : 'text-blue-600 dark:text-blue-400'"
        >
          {{ displayName?.charAt(0)?.toUpperCase() || '?' }}
        </span>
      </div>

      <div class="flex-1 min-w-0">
        <p class="font-semibold text-gray-900 dark:text-white truncate">{{ displayName }}</p>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ type === 'incoming' ? 'Möchte dein Freund werden' : 'Anfrage gesendet' }}
        </p>
        <p v-if="formattedDate" class="text-xs text-gray-400 dark:text-gray-500 mt-1">
          {{ formattedDate }}
        </p>
      </div>

      <!-- Actions for incoming requests -->
      <div v-if="type === 'incoming'" class="flex items-center gap-2 shrink-0">
        <button
            @click="handleAccept"
            :disabled="loading"
            class="px-3 py-2 rounded-lg font-medium text-sm text-white bg-linear-to-r from-green-500 to-emerald-500 hover:from-green-600 hover:to-emerald-600 transition-all disabled:opacity-50"
        >
          <Icon name="lucide:check" class=" mr-1" />
          Annehmen
        </button>
        <button
            @click="handleDecline"
            :disabled="loading"
            class="px-3 py-2 rounded-lg font-medium text-sm text-red-600 dark:text-red-400 border-2 border-red-300 dark:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20 transition-all disabled:opacity-50"
        >
          <Icon name="lucide:x" class=" mr-1" />
          Ablehnen
        </button>
      </div>

      <!-- Actions for outgoing requests -->
      <div v-else class="shrink-0">
        <button
            @click="handleCancel"
            :disabled="loading"
            class="px-3 py-2 rounded-lg font-medium text-sm text-gray-600 dark:text-gray-400 border-2 border-gray-300 dark:border-neutral-600 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-all disabled:opacity-50"
        >
          Zurückziehen
        </button>
      </div>
    </div>
  </div>
</template>
