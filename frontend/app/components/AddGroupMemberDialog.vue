<script setup lang="ts">
import {useFriends} from '~/composables/useFriends';
import type {User} from '~/types';

const props = defineProps<{
  visible: boolean;
  existingMemberIds: number[];
}>();

const emit = defineEmits<{
  close: [];
  add: [userId: number];
}>();

const {searchFriends, loading: searching} = useFriends();

const searchQuery = ref('');
const searchResults = ref<any[]>([]);
const selectedUser = ref<any>(null);

const filteredResults = computed(() => {
  // Filter out users who are already members
  return searchResults.value.filter(user => !props.existingMemberIds.includes(user.user_id));
});

const handleSearch = async () => {
  if (searchQuery.value.length < 2) {
    searchResults.value = [];
    return;
  }

  searchResults.value = await searchFriends(searchQuery.value);
};

const selectUser = (user: any) => {
  selectedUser.value = user;
};

const close = () => {
  searchQuery.value = '';
  searchResults.value = [];
  selectedUser.value = null;
  emit('close');
};

const add = () => {
  if (!selectedUser.value) return;
  emit('add', selectedUser.value.user_id);
  close();
};

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    searchQuery.value = '';
    searchResults.value = [];
    selectedUser.value = null;
  }
});
</script>

<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div class="w-full max-w-md bg-white dark:bg-neutral-800 rounded-xl shadow-2xl transform transition-all max-h-[90vh] flex flex-col">
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-neutral-700 shrink-0">
        <div class="flex items-center gap-3">
          <div class="w-12 h-12 bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30 rounded-xl flex items-center justify-center shrink-0">
            <Icon name="lucide:user-plus" class=" text-blue-600 dark:text-blue-400 text-xl" />
          </div>
          <h2 class="text-xl font-bold text-gray-900 dark:text-white">Mitglied hinzufügen</h2>
        </div>
        <button
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
            @click="close"
        >
          <Icon name="lucide:x" class=" text-gray-500 dark:text-gray-400 text-lg" />
        </button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-4 overflow-y-auto flex-1">
        <!-- Search -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Freund suchen
          </label>
          <div class="relative">
            <Icon name="lucide:search" class=" absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
                v-model="searchQuery"
                type="text"
                placeholder="Name eingeben..."
                class="w-full pl-10 pr-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-blue-500 dark:focus:border-blue-400 focus:ring-2 focus:ring-blue-200 dark:focus:ring-blue-900/50 outline-none transition-all"
                @input="handleSearch"
            />
          </div>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-2">
            Du kannst nur Freunde zur Gruppe hinzufügen.
          </p>
        </div>

        <!-- Results -->
        <div v-if="searchQuery.length >= 2">
          <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-3">
            Suchergebnisse
          </h3>

          <div v-if="searching" class="text-center py-6">
            <i class="pi animate-spin text-xl text-blue-600 dark:text-blue-400" />
            <p class="text-gray-500 dark:text-gray-400 mt-2 text-sm">Wird gesucht...</p>
          </div>

          <div v-else-if="filteredResults.length === 0" class="text-center py-6">
            <Icon name="lucide:search" class=" text-2xl text-gray-300 dark:text-gray-600 mb-2" />
            <p class="text-gray-500 dark:text-gray-400 text-sm">Keine Freunde gefunden</p>
          </div>

          <div v-else class="space-y-2 max-h-48 overflow-y-auto">
            <button
                v-for="result in filteredResults"
                :key="result.user_id"
                class="w-full flex items-center gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors text-left"
                :class="selectedUser?.user_id === result.user_id ? 'bg-blue-50 dark:bg-blue-900/20 border-2 border-blue-500' : ''"
                @click="selectUser(result)"
            >
              <div class="w-10 h-10 rounded-full flex items-center justify-center shrink-0 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30">
                <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
                  {{ result.name?.charAt(0)?.toUpperCase() || '?' }}
                </span>
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-gray-900 dark:text-white truncate">{{ result.name }}</p>
                <p v-if="result.email" class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ result.email }}</p>
              </div>
              <Icon v-if="selectedUser?.user_id === result.user_id" name="lucide:check" class="text-blue-500" />
            </button>
          </div>
        </div>

        <!-- Selected User -->
        <div v-if="selectedUser" class="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full flex items-center justify-center shrink-0 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30">
              <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
                {{ selectedUser.name?.charAt(0)?.toUpperCase() || '?' }}
              </span>
            </div>
            <div class="flex-1 min-w-0">
              <p class="font-medium text-gray-900 dark:text-white">{{ selectedUser.name }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Wird zur Gruppe hinzugefügt</p>
            </div>
            <button
                class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg transition-colors"
                @click="selectedUser = null"
            >
              <Icon name="lucide:x" />
            </button>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex flex-col-reverse sm:flex-row justify-end gap-3 p-6 border-t border-gray-200 dark:border-neutral-700 shrink-0">
        <button
            class="px-5 py-2.5 rounded-lg font-medium transition-all border-2 border-gray-300 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-neutral-700"
            @click="close"
        >
          Abbrechen
        </button>

        <button
            class="px-5 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            :disabled="!selectedUser"
            @click="add"
        >
          <Icon name="lucide:plus" />
          <span>Hinzufügen</span>
        </button>
      </div>
    </div>
  </div>
</template>
