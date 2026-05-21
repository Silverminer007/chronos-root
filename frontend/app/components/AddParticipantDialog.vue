<script setup lang="ts">
import {useFriends} from "~/composables/useFriends";

interface Props {
  visible: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  add: [data: { id: number; role: 'ATTENDANT' | 'RESPONSIBLE' | 'GUEST' | 'HELPER' }];
}>();

const {searchFriends, loading: searching} = useFriends();

const searchQuery = ref('');
const selectedRole = ref<'ATTENDANT' | 'RESPONSIBLE' | 'GUEST' | 'HELPER'>('ATTENDANT');
const searchResults = ref<any[]>([]);
const selectedItem = ref<any>(null);

const handleSearch = async () => {
  if (searchQuery.value.length < 2) {
    searchResults.value = [];
    return;
  }

  searchResults.value = await searchFriends(searchQuery.value);
};

const selectResult = (result: any) => {
  selectedItem.value = result;
};

const getRoleLabel = (role: string) => {
  const labels: Record<string, string> = {
    RESPONSIBLE: 'Organisator',
    ATTENDANT: 'Teilnehmer',
    HELPER: 'Helfer',
    GUEST: 'Gast'
  };
  return labels[role] || role;
};

const close = () => {
  searchQuery.value = '';
  searchResults.value = [];
  selectedItem.value = null;
  emit('close');
};

const add = () => {
  if (!selectedItem.value) return;

  emit('add', {
    id: selectedItem.value.user_id,
    role: selectedRole.value
  });

  close();
};

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    searchQuery.value = '';
    searchResults.value = [];
    selectedItem.value = null;
  }
});
</script>

<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div
        class="w-full max-w-2xl bg-white dark:bg-neutral-800 rounded-xl shadow-2xl transform transition-all max-h-[90vh] flex flex-col">
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-neutral-700 shrink-0">
        <div class="flex items-center gap-3">
          <div
              class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center shrink-0">
            <Icon name="lucide:user-plus" class=" text-purple-600 dark:text-purple-400 text-xl" />
          </div>
          <h2 class="text-xl font-bold text-gray-900 dark:text-white">Teilnehmer hinzufügen</h2>
        </div>
        <button
            @click="close"
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
        >
          <Icon name="lucide:x" class=" text-gray-500 dark:text-gray-400 text-lg" />
        </button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-6 overflow-y-auto flex-1">
        <!-- Search -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Person suchen
          </label>
          <div class="relative">
            <Icon name="lucide:search" class=" absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
                v-model="searchQuery"
                type="text"
                placeholder="Name eingeben..."
                class="w-full pl-10 pr-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
                @input="handleSearch"
            />
          </div>
        </div>

        <!-- Role Selection -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Rolle
          </label>
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <button
                @click="selectedRole = 'RESPONSIBLE'"
                class="p-3 rounded-lg border-2 transition-all"
                :class="selectedRole === 'RESPONSIBLE'
                ? 'border-purple-600 dark:border-purple-400 bg-purple-50 dark:bg-purple-900/20'
                : 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'"
            >
              <Icon name="lucide:star" class=" text-purple-600 dark:text-purple-400 mb-1" />
              <p class="text-sm font-medium text-gray-900 dark:text-white">Organisator</p>
            </button>
            <button
                @click="selectedRole = 'ATTENDANT'"
                class="p-3 rounded-lg border-2 transition-all"
                :class="selectedRole === 'ATTENDANT'
                ? 'border-blue-600 dark:border-blue-400 bg-blue-50 dark:bg-blue-900/20'
                : 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'"
            >
              <Icon name="lucide:user" class=" text-blue-600 dark:text-blue-400 mb-1" />
              <p class="text-sm font-medium text-gray-900 dark:text-white">Teilnehmer</p>
            </button>
            <button
                @click="selectedRole = 'HELPER'"
                class="p-3 rounded-lg border-2 transition-all"
                :class="selectedRole === 'HELPER'
                ? 'border-green-600 dark:border-green-400 bg-green-50 dark:bg-green-900/20'
                : 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'"
            >
              <Icon name="lucide:wrench" class=" text-green-600 dark:text-green-400 mb-1" />
              <p class="text-sm font-medium text-gray-900 dark:text-white">Helfer</p>
            </button>
            <button
                @click="selectedRole = 'GUEST'"
                class="p-3 rounded-lg border-2 transition-all"
                :class="selectedRole === 'GUEST'
                ? 'border-gray-600 dark:border-gray-400 bg-gray-50 dark:bg-gray-800/50'
                : 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'"
            >
              <Icon name="lucide:eye" class=" text-gray-600 dark:text-gray-400 mb-1" />
              <p class="text-sm font-medium text-gray-900 dark:text-white">Gast</p>
            </button>
          </div>
        </div>

        <!-- Results -->
        <div v-if="searchQuery.length > 0">
          <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-3">
            Suchergebnisse
          </h3>

          <div v-if="searching" class="text-center py-8">
            <Icon name="lucide:loader-2" class="animate-spin text-2xl text-purple-600 dark:text-purple-400" />
            <p class="text-gray-500 dark:text-gray-400 mt-2">Wird gesucht...</p>
          </div>

          <div v-else-if="searchResults.length === 0" class="text-center py-8">
            <Icon name="lucide:search" class=" text-3xl text-gray-300 dark:text-gray-600 mb-2" />
            <p class="text-gray-500 dark:text-gray-400">Keine Ergebnisse gefunden</p>
          </div>

          <div v-else class="space-y-2 max-h-60 overflow-y-auto">
            <button
                v-for="result in searchResults"
                :key="result.id"
                @click="selectResult(result)"
                class="w-full flex items-center gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors text-left"
            >
              <div
                  class="w-10 h-10 rounded-full flex items-center justify-center shrink-0 bg-linear-to-br
                  from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30"
              >
                <Icon name="lucide:user" class="text-purple-600 dark:text-purple-400" />
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-gray-900 dark:text-white truncate">{{ result.name }}</p>
                <p v-if="result.email" class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ result.email }}</p>
              </div>
            </button>
          </div>
        </div>

        <!-- Selected Item -->
        <div v-if="selectedItem"
             class="p-4 bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 rounded-lg">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div
                  class="w-10 h-10 rounded-full flex items-center justify-center shrink-0 bg-linear-to-br
                   from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30"
              >
                <Icon name="lucide:user" class="text-purple-600 dark:text-purple-400" />
              </div>
              <div>
                <p class="font-medium text-gray-900 dark:text-white">{{ selectedItem.name }}</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">Als {{ getRoleLabel(selectedRole) }}</p>
              </div>
            </div>
            <button
                @click="selectedItem = null"
                class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-neutral-700 rounded-lg transition-colors"
            >
              <Icon name="lucide:x" />
            </button>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div
          class="flex flex-col-reverse sm:flex-row justify-end gap-3 p-6 border-t border-gray-200 dark:border-neutral-700 shrink-0">
        <button
            class="px-5 py-2.5 rounded-lg font-medium transition-all border-2 border-gray-300 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-neutral-700"
            @click="close"
        >
          Abbrechen
        </button>

        <button
            class="px-5 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            :disabled="!selectedItem"
            @click="add"
        >
          <Icon name="lucide:plus" />
          <span>Hinzufügen</span>
        </button>
      </div>
    </div>
  </div>
</template>
