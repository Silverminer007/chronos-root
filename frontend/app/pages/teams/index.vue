<script setup lang="ts">
import {useTeamsStore} from '~/stores/teams';
import {useToast} from 'primevue/usetoast';

const teamsStore = useTeamsStore();
const toast = useToast();

const showCreateDialog = ref(false);
const newTeamName = ref('');
const creating = ref(false);

onMounted(async () => {
  await teamsStore.fetchMyTeams();
});

const handleCreateTeam = async () => {
  if (!newTeamName.value.trim()) return;
  creating.value = true;
  try {
    const team = await teamsStore.createTeam(newTeamName.value.trim());
    toast.add({severity: 'success', summary: 'Team erstellt', detail: `"${team?.name}" wurde erstellt`, life: 3000});
    showCreateDialog.value = false;
    newTeamName.value = '';
    if (team) navigateTo(`/teams/${team.id}`);
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: 'Team konnte nicht erstellt werden', life: 3000});
  } finally {
    creating.value = false;
  }
};

const openCreateDialog = () => {
  newTeamName.value = '';
  showCreateDialog.value = true;
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
            <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Teams</h1>
            <p class="text-gray-500 dark:text-gray-400 mt-1">Verwalte deine Teams und Mitglieder</p>
          </div>
          <button
              class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg items-center gap-2"
              @click="openCreateDialog"
          >
            <Icon name="lucide:plus" />
            <span>Neues Team</span>
          </button>
        </div>

        <!-- Loading State -->
        <div v-if="teamsStore.loading" class="text-center py-16">
          <i class="pi animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400">Wird geladen...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="teamsStore.error" class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <Icon name="lucide:triangle-alert" class="text-red-600 dark:text-red-400 text-xl" />
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ teamsStore.error }}</p>
            </div>
          </div>
        </div>

        <!-- Teams List -->
        <div v-else class="space-y-4">
          <NuxtLink
              v-for="team in teamsStore.teams"
              :key="team.id"
              :to="`/teams/${team.id}`"
              class="block bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-neutral-700 hover:border-purple-300 dark:hover:border-purple-600 hover:shadow-md transition-all p-5"
          >
            <div class="flex items-center justify-between gap-4">
              <div class="flex items-center gap-4">
                <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center shrink-0">
                  <Icon name="lucide:shield-check" class="text-purple-600 dark:text-purple-400 text-xl" />
                </div>
                <div>
                  <h3 class="font-semibold text-gray-900 dark:text-white">{{ team.name }}</h3>
                  <p class="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                    {{ team.members?.length || 0 }}
                    {{ (team.members?.length || 0) === 1 ? 'Mitglied' : 'Mitglieder' }}
                  </p>
                </div>
              </div>
              <Icon name="lucide:chevron-right" class="text-gray-400 dark:text-gray-500 shrink-0" />
            </div>
          </NuxtLink>

          <!-- Empty State -->
          <div v-if="teamsStore.teams.length === 0" class="text-center py-16 px-6">
            <div class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
              <Icon name="lucide:shield-check" class="text-3xl text-purple-600 dark:text-purple-400" />
            </div>
            <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Noch keine Teams</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">Erstelle ein Team oder tritt einem über einen Einladungslink bei.</p>
            <button
                class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg"
                @click="openCreateDialog"
            >
              <Icon name="lucide:plus" class="mr-2" />
              Erstes Team erstellen
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- FAB (Mobile) -->
    <button
        class="fixed bottom-6 right-6 w-14 h-14 sm:hidden rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all transform hover:scale-110 z-50"
        @click="openCreateDialog"
    >
      <Icon name="lucide:plus" class="text-xl" />
    </button>

    <!-- Create Team Dialog -->
    <Dialog v-model:visible="showCreateDialog" modal header="Neues Team erstellen" class="w-full max-w-md mx-4">
      <div class="space-y-4 pt-2">
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Teamname</label>
          <input
              v-model="newTeamName"
              type="text"
              placeholder="z.B. DLRG Jugend Musterstadt"
              class="w-full px-4 py-2.5 rounded-lg border-2 border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
              @keyup.enter="handleCreateTeam"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button
              class="px-4 py-2 rounded-lg font-medium text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-neutral-600 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors"
              @click="showCreateDialog = false"
          >
            Abbrechen
          </button>
          <button
              :disabled="!newTeamName.trim() || creating"
              class="px-4 py-2 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
              @click="handleCreateTeam"
          >
            {{ creating ? 'Wird erstellt...' : 'Erstellen' }}
          </button>
        </div>
      </div>
    </Dialog>

    <Toast />
  </div>
</template>
