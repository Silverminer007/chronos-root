<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {useGroupsStore} from '~/stores/groups';
import {useToast} from 'primevue/usetoast';

const {fetchUser} = useAuthStore();
await fetchUser();

const groupsStore = useGroupsStore();
const toast = useToast();

const showCreateDialog = ref(false);

onMounted(async () => {
  await groupsStore.fetchGroups();
});

const handleDeleteGroup = async (groupId: number) => {
  try {
    await groupsStore.deleteGroup(groupId);
    toast.add({
      severity: 'success',
      summary: 'Gruppe gelöscht',
      detail: 'Die Gruppe wurde gelöscht',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Gruppe konnte nicht gelöscht werden',
      life: 3000
    });
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Page Header -->
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Gruppen</h1>
            <p class="text-gray-500 dark:text-gray-400 mt-1">Verwalte deine Gruppen für Termine</p>
          </div>
          <button
              @click="showCreateDialog = true"
              class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 transition-all shadow-lg items-center gap-2"
          >
            <i class="pi pi-plus"></i>
            <span>Neue Gruppe</span>
          </button>
        </div>

        <!-- Loading State -->
        <div v-if="groupsStore.loading" class="text-center py-16">
          <i class="pi pi-spin pi-spinner text-4xl text-blue-600 dark:text-blue-400 mb-4"></i>
          <p class="text-gray-600 dark:text-gray-400">Wird geladen...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="groupsStore.error" class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <i class="pi pi-exclamation-triangle text-red-600 dark:text-red-400 text-xl"></i>
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ groupsStore.error }}</p>
            </div>
          </div>
        </div>

        <!-- Groups List -->
        <div v-else class="space-y-4">
          <GroupCard
              v-for="group in groupsStore.groups"
              :key="group.id"
              :group="group"
              @delete="handleDeleteGroup"
          />

          <!-- Empty State -->
          <div v-if="groupsStore.groups.length === 0" class="text-center py-16 px-6">
            <div class="w-20 h-20 bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
              <i class="pi pi-users text-3xl text-blue-600 dark:text-blue-400"></i>
            </div>
            <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Noch keine Gruppen</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">Erstelle Gruppen, um mehrere Personen gleichzeitig zu Terminen einzuladen.</p>
            <button
                @click="showCreateDialog = true"
                class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 transition-all shadow-lg"
            >
              <i class="pi pi-plus mr-2"></i>
              Erste Gruppe erstellen
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- FAB - Create Group (Mobile) -->
    <button
        @click="showCreateDialog = true"
        class="fixed bottom-6 right-6 w-14 h-14 sm:hidden rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 transition-all transform hover:scale-110 z-50"
    >
      <i class="pi pi-plus text-xl"></i>
    </button>

    <!-- Create Group Dialog -->
    <CreateGroupDialog
        :visible="showCreateDialog"
        @close="showCreateDialog = false"
    />

    <Toast />
  </div>
</template>
