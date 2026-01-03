<script setup lang="ts">
import type {Friend} from '~/types';
import {DateTime} from 'luxon';
import ConfirmDialog from '~/components/ConfirmDialog.vue';

const props = defineProps<{
  friend: Friend;
}>();

const emit = defineEmits<{
  remove: [friendId: number];
}>();

const showConfirmDialog = ref(false);

const formattedFriendsSince = computed(() => {
  if (!props.friend.friends_since) return '';
  return DateTime.fromISO(props.friend.friends_since).toLocaleString(DateTime.DATE_MED);
});

const handleRemove = () => {
  emit('remove', props.friend.user_id);
  showConfirmDialog.value = false;
};
</script>

<template>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-4">
    <div class="flex items-center gap-4">
      <div
          class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
        <span class="text-purple-600 dark:text-purple-400 font-semibold text-lg">
          {{ friend.name?.charAt(0)?.toUpperCase() || '?' }}
        </span>
      </div>

      <div class="flex-1 min-w-0">
        <p class="font-semibold text-gray-900 dark:text-white truncate">{{ friend.name }}</p>
        <p class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ friend.email }}</p>
        <p v-if="formattedFriendsSince" class="text-xs text-gray-400 dark:text-gray-500 mt-1">
          Freunde seit {{ formattedFriendsSince }}
        </p>
      </div>

      <button
          @click="showConfirmDialog = true"
          class="p-2 text-gray-400 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
          title="Freundschaft beenden"
      >
        <Icon name="lucide:trash-2" />
      </button>
    </div>
  </div>

  <ConfirmDialog
      :visible="showConfirmDialog"
      title="Freundschaft beenden"
      :message="`Möchtest du die Freundschaft mit ${friend.name} wirklich beenden?`"
      confirm-text="Beenden"
      confirm-color="red"
      @close="showConfirmDialog = false"
      @confirm="handleRemove"
  />
</template>
