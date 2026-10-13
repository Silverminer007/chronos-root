<script setup lang="ts">
import {useGroupsStore} from '~/stores/groups';
import {useTeamsStore} from '~/stores/teams';
import {useToast} from 'primevue/usetoast';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  close: [];
}>();

const groupsStore = useGroupsStore();
const teamsStore = useTeamsStore();
const toast = useToast();

const groupName = ref('');
const selectedTeamId = ref<number | null>(null);
const loading = ref(false);
const error = ref('');

const isValid = computed(() => groupName.value.trim().length >= 2 && selectedTeamId.value !== null);

const close = () => {
  groupName.value = '';
  selectedTeamId.value = null;
  error.value = '';
  emit('close');
};

const createGroup = async () => {
  if (!isValid.value || selectedTeamId.value === null) {
    error.value = 'Gruppenname (mind. 2 Zeichen) und Team sind erforderlich';
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    await groupsStore.createGroup(groupName.value.trim(), selectedTeamId.value);
    toast.add({
      severity: 'success',
      summary: 'Gruppe erstellt',
      detail: `Die Gruppe "${groupName.value}" wurde erstellt`,
      life: 3000
    });
    close();
  } catch (err) {
    error.value = getErrorMessage(err, 'Fehler beim Erstellen der Gruppe');
  } finally {
    loading.value = false;
  }
};

watch(() => props.visible, async (newVal) => {
  if (newVal) {
    if (teamsStore.teams.length === 0) await teamsStore.fetchMyTeams();
    if (teamsStore.teams.length === 1) selectedTeamId.value = teamsStore.teams[0]!.id;
  } else {
    groupName.value = '';
    selectedTeamId.value = null;
    error.value = '';
  }
});
</script>

<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div class="w-full max-w-md bg-white dark:bg-neutral-800 rounded-xl shadow-2xl transform transition-all">
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-neutral-700">
        <div class="flex items-center gap-3">
          <div class="w-12 h-12 bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30 rounded-xl flex items-center justify-center shrink-0">
            <Icon name="lucide:users" class="text-blue-600 dark:text-blue-400 text-xl" />
          </div>
          <h2 class="text-xl font-bold text-gray-900 dark:text-white">Neue Gruppe</h2>
        </div>
        <button
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
            @click="close"
        >
          <Icon name="lucide:x" class="text-gray-500 dark:text-gray-400 text-lg" />
        </button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-4">
        <!-- Group name -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Gruppenname
          </label>
          <div class="relative">
            <Icon name="lucide:tag" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
                v-model="groupName"
                type="text"
                placeholder="z.B. Leiterrunde, Helfer-Team..."
                class="w-full pl-10 pr-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-blue-500 dark:focus:border-blue-400 focus:ring-2 focus:ring-blue-200 dark:focus:ring-blue-900/50 outline-none transition-all"
                @keyup.enter="createGroup"
            />
          </div>
        </div>

        <!-- Team selector -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Team
          </label>
          <div class="relative">
            <Icon name="lucide:shield-check" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
            <select
                v-model="selectedTeamId"
                class="w-full pl-10 pr-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white focus:border-blue-500 dark:focus:border-blue-400 focus:ring-2 focus:ring-blue-200 dark:focus:ring-blue-900/50 outline-none transition-all appearance-none"
            >
              <option :value="null" disabled>Team auswählen...</option>
              <option v-for="team in teamsStore.teams" :key="team.id" :value="team.id">
                {{ team.name }}
              </option>
            </select>
          </div>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-2">
            Nur Mitglieder dieses Teams können zur Gruppe hinzugefügt werden.
          </p>
        </div>

        <div v-if="error" class="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
          <p class="text-sm text-red-600 dark:text-red-400">{{ error }}</p>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex flex-col-reverse sm:flex-row justify-end gap-3 p-6 border-t border-gray-200 dark:border-neutral-700">
        <button
            class="px-5 py-2.5 rounded-lg font-medium transition-all border-2 border-gray-300 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-neutral-700"
            @click="close"
        >
          Abbrechen
        </button>

        <button
            class="px-5 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            :disabled="!isValid || loading"
            @click="createGroup"
        >
          <Icon :name="loading ? 'lucide:loader-2' : 'lucide:plus'" :class="{ 'animate-spin': loading }" />
          <span>Erstellen</span>
        </button>
      </div>
    </div>
  </div>
</template>
