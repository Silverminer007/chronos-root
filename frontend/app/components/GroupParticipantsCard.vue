<script setup lang="ts">
import type {Appointment, ParticipantGroup, Role} from '~/types';
import {useAppointmentsStore} from '~/stores/appointments';
import {useAuthStore} from '~/stores/auth';
import {useGroups} from '~/composables/useGroups';
import {useToast} from 'primevue/usetoast';

const {appointment} = defineProps<{
  appointment: Appointment;
}>();

const appointmentsStore = useAppointmentsStore();
const authStore = useAuthStore();
const {searchGroups, loading: searchLoading} = useGroups();
const toast = useToast();

// Check if current user is responsible
const isResponsible = computed(() => {
  if (!appointment || !authStore.user?.id) return false;
  return appointment.participants?.some(
      p => p.user_id === authStore.user?.id && p.role === 'RESPONSIBLE'
  ) || false;
});

// Get unique groups from participants
const groups = computed(() => appointment.group_participants);

// Get participants for a specific group
const getGroupParticipants = (groupId: number) => {
  const participants = appointmentsStore.getParticipantsByGroup(appointment, groupId);
  if (!participants) return [];

  const statusPriority: Record<string, number> = {APPROVED: 0, REJECTED: 1, PENDING: 2};
  return [...participants].sort((a, b) => {
    const statusOrder = (statusPriority[a.status] ?? 2) - (statusPriority[b.status] ?? 2);
    if (statusOrder !== 0) return statusOrder;
    return a.name.localeCompare(b.name);
  });
};

// Get approved count for a group
const getGroupApprovedCount = (groupId: number) => {
  return getGroupParticipants(groupId).filter(p => p.status === 'APPROVED').length;
};

// Expanded groups state
const expandedGroups = ref<Set<number>>(new Set());

const toggleGroup = (groupId: number) => {
  if (expandedGroups.value.has(groupId)) {
    expandedGroups.value.delete(groupId);
  } else {
    expandedGroups.value.add(groupId);
  }
};

const isExpanded = (groupId: number) => expandedGroups.value.has(groupId);

// Add group dialog state
const showAddDialog = ref(false);
const searchQuery = ref('');
const searchResults = ref<any[]>([]);
const selectedGroup = ref<any>(null);
const selectedRole = ref<Role>('ATTENDANT');
const adding = ref(false);

const handleSearch = async () => {
  searchResults.value = await searchGroups(searchQuery.value);
};
await handleSearch();

const selectGroup = (group: any) => {
  selectedGroup.value = group;
};

const openAddDialog = () => {
  showAddDialog.value = true;
  searchQuery.value = '';
  searchResults.value = [];
  selectedGroup.value = null;
  selectedRole.value = 'ATTENDANT';
};

const closeAddDialog = () => {
  showAddDialog.value = false;
  searchQuery.value = '';
  searchResults.value = [];
  selectedGroup.value = null;
};

const addGroup = async () => {
  if (!selectedGroup.value) return;

  adding.value = true;
  try {
    await appointmentsStore.addGroupParticipant(
        appointment.id,
        selectedGroup.value.id,
        selectedRole.value
    );
    toast.add({
      severity: 'success',
      summary: 'Gruppe hinzugefügt',
      detail: `"${selectedGroup.value.name}" wurde zum Termin hinzugefügt`,
      life: 3000
    });
    closeAddDialog();
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Gruppe konnte nicht hinzugefügt werden',
      life: 3000
    });
  } finally {
    adding.value = false;
  }
};

const getRoleLabel = (role: string) => {
  const labels: Record<string, string> = {
    RESPONSIBLE: 'Organisatoren',
    ATTENDANT: 'Teilnehmer',
    HELPER: 'Helfer',
    GUEST: 'Gäste',
    NONE: ''
  };
  return labels[role] || role;
};

const getRoleLabelSingular = (role: string) => {
  const labels: Record<string, string> = {
    RESPONSIBLE: 'Organisator',
    ATTENDANT: 'Teilnehmer',
    HELPER: 'Helfer',
    GUEST: 'Gast'
  };
  return labels[role] || role;
};

const getRoleBadgeClass = (role: string) => {
  const classes: Record<string, string> = {
    RESPONSIBLE: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    ATTENDANT: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    HELPER: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    GUEST: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-400'
  };
  return classes[role] || classes.GUEST;
};

const getStatusIconName = (status: string) => {
  const icons: Record<string, string> = {
    APPROVED: 'lucide:check',
    REJECTED: 'lucide:x',
    PENDING: 'lucide:clock'
  };
  return icons[status] || '';
};

const getStatusIconClass = (status: string) => {
  const classes: Record<string, string> = {
    APPROVED: 'text-green-600 dark:text-green-400',
    REJECTED: 'text-red-600 dark:text-red-400',
    PENDING: 'text-yellow-600 dark:text-yellow-400'
  };
  return classes[status] || '';
};

const roleOptions = [
  {value: 'RESPONSIBLE', label: 'Organisator', icon: 'lucide:star', color: 'purple'},
  {value: 'ATTENDANT', label: 'Teilnehmer', icon: 'lucide:user', color: 'blue'},
  {value: 'HELPER', label: 'Helfer', icon: 'lucide:wrench', color: 'green'},
  {value: 'GUEST', label: 'Gast', icon: 'lucide:eye', color: 'gray'}
];

const getRoleButtonClass = (role: string) => {
  const isSelected = selectedRole.value === role;
  const colors: Record<string, { selected: string; unselected: string }> = {
    RESPONSIBLE: {
      selected: 'border-purple-600 dark:border-purple-400 bg-purple-50 dark:bg-purple-900/20',
      unselected: 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'
    },
    ATTENDANT: {
      selected: 'border-blue-600 dark:border-blue-400 bg-blue-50 dark:bg-blue-900/20',
      unselected: 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'
    },
    HELPER: {
      selected: 'border-green-600 dark:border-green-400 bg-green-50 dark:bg-green-900/20',
      unselected: 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'
    },
    GUEST: {
      selected: 'border-gray-600 dark:border-gray-400 bg-gray-50 dark:bg-gray-800/50',
      unselected: 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'
    }
  };
  return isSelected ? colors[role]?.selected : colors[role]?.unselected;
};

const getRoleIconClass = (role: string) => {
  const classes: Record<string, string> = {
    RESPONSIBLE: 'text-purple-600 dark:text-purple-400',
    ATTENDANT: 'text-blue-600 dark:text-blue-400',
    HELPER: 'text-green-600 dark:text-green-400',
    GUEST: 'text-gray-600 dark:text-gray-400'
  };
  return classes[role] || '';
};
</script>

<template>
  <div
      class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
    <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div
              class="w-10 h-10 bg-linear-to-br from-indigo-100 to-purple-100 dark:from-indigo-900/30 dark:to-purple-900/30 rounded-lg flex items-center justify-center">
            <Icon name="lucide:users" class=" text-indigo-600 dark:text-indigo-400"/>
          </div>
          <div>
            <h3 class="text-lg font-bold text-gray-900 dark:text-white">Gruppen</h3>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              {{ groups.length }} {{ groups.length === 1 ? 'Gruppe' : 'Gruppen' }} eingeladen
            </p>
          </div>
        </div>
        <!-- Add Group Button (only for responsible users) -->
        <button
            v-if="isResponsible"
            class="w-8 h-8 flex items-center justify-center rounded-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 text-white transition-all shadow-sm"
            title="Gruppe hinzufügen"
            @click="openAddDialog"
        >
          <Icon name="lucide:plus"/>
        </button>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="groups.length === 0" class="p-8 text-center">
      <div class="w-16 h-16 bg-gray-100 dark:bg-neutral-700 rounded-full flex items-center justify-center mx-auto mb-4">
        <Icon name="lucide:users" class=" text-2xl text-gray-400 dark:text-gray-500"/>
      </div>
      <p class="text-gray-500 dark:text-gray-400">Keine Gruppen eingeladen</p>
      <p class="text-sm text-gray-400 dark:text-gray-500 mt-1">
        Teilnehmer wurden einzeln hinzugefügt
      </p>
      <button
          v-if="isResponsible"
          class="mt-4 px-4 py-2 rounded-lg font-medium text-purple-600 dark:text-purple-400 border-2 border-purple-600 dark:border-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 transition-all"
          @click="openAddDialog"
      >
        <Icon name="lucide:plus" class=" mr-2"/>
        Gruppe hinzufügen
      </button>
    </div>

    <!-- Groups List -->
    <div v-else class="divide-y divide-gray-200 dark:divide-neutral-700">
      <div
          v-for="group in groups"
          :key="group.id"
          class="group"
      >
        <!-- Group Header -->
        <button
            class="w-full p-4 flex items-center justify-between hover:bg-gray-50 dark:hover:bg-neutral-700/50 transition-colors"
            @click="toggleGroup(group.id)"
        >
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 bg-indigo-100 dark:bg-indigo-900/30 rounded-full flex items-center justify-center">
              <Icon name="lucide:users" class=" text-indigo-600 dark:text-indigo-400"/>
            </div>
            <div class="text-left">
              <p class="font-medium text-gray-900 dark:text-white">{{ group.name }}</p>
              <div class="flex items-center gap-2 mt-1">
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  {{ getGroupApprovedCount(group.id) }}/{{ getGroupParticipants(group.id).length }} zugesagt
                </span>
              </div>
            </div>
          </div>
          <Icon
              :name="isExpanded(group.id) ? 'lucide:chevron-up' : 'lucide:chevron-down'"
              class="text-gray-400 transition-transform duration-200"
          />
        </button>

        <!-- Group Members (Expandable) -->
        <div
            v-show="isExpanded(group.id)"
            class="bg-gray-50 dark:bg-neutral-900/50 border-t border-gray-200 dark:border-neutral-700"
        >
          <div class="divide-y divide-gray-200 dark:divide-neutral-700">
            <div
                v-for="participant in getGroupParticipants(group.id)"
                :key="participant.user_id"
                class="px-4 py-3 pl-14 flex items-center justify-between"
            >
              <div class="flex items-center gap-3">
                <div
                    class="w-8 h-8 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center">
                  <span class="text-purple-600 dark:text-purple-400 font-semibold text-xs">
                    {{ participant.name?.charAt(0)?.toUpperCase() || '?' }}
                  </span>
                </div>
                <span class="text-sm text-gray-700 dark:text-gray-300">{{ participant.name }}</span>
              </div>
              <Icon
:name="getStatusIconName(participant.status)" :class="getStatusIconClass(participant.status)"
                    class="text-sm"/>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Add Group Dialog -->
  <div v-if="showAddDialog" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div
        class="w-full max-w-lg bg-white dark:bg-neutral-800 rounded-xl shadow-2xl transform transition-all max-h-[90vh] flex flex-col">
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-neutral-700 shrink-0">
        <div class="flex items-center gap-3">
          <div
              class="w-12 h-12 bg-linear-to-br from-indigo-100 to-purple-100 dark:from-indigo-900/30 dark:to-purple-900/30 rounded-xl flex items-center justify-center shrink-0">
            <Icon name="lucide:users" class=" text-indigo-600 dark:text-indigo-400 text-xl"/>
          </div>
          <h2 class="text-xl font-bold text-gray-900 dark:text-white">Gruppe hinzufügen</h2>
        </div>
        <button
            class="w-10 h-10 flex items-center justify-center rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
            @click="closeAddDialog"
        >
          <Icon name="lucide:x" class=" text-gray-500 dark:text-gray-400 text-lg"/>
        </button>
      </div>

      <!-- Content -->
      <div class="p-6 space-y-6 overflow-y-auto flex-1">
        <!-- Search -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Gruppe suchen
          </label>
          <div class="relative">
            <Icon name="lucide:search" class=" absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"/>
            <input
                v-model="searchQuery"
                type="text"
                placeholder="Gruppenname eingeben..."
                class="w-full pl-10 pr-4 py-3 rounded-lg border-2 border-gray-300 dark:border-neutral-600 bg-white dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
                @input="handleSearch"
            />
          </div>
        </div>

        <!-- Role Selection -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Rolle für Gruppenmitglieder
          </label>
          <div class="grid grid-cols-2 gap-3">
            <button
                v-for="option in roleOptions"
                :key="option.value"
                class="p-3 rounded-lg border-2 transition-all text-center"
                :class="getRoleButtonClass(option.value)"
                @click="selectedRole = option.value as Role"
            >
              <Icon :name="option.icon" :class="getRoleIconClass(option.value)" class="mb-1"/>
              <p class="text-sm font-medium text-gray-900 dark:text-white">{{ option.label }}</p>
            </button>
          </div>
        </div>

        <!-- Search Results -->
        <div v-if="searchQuery.length > 0">
          <h3 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-3">
            Suchergebnisse
          </h3>

          <div v-if="searchLoading" class="text-center py-8">
            <Icon name="lucide:loader-2" class="animate-spin text-2xl text-purple-600 dark:text-purple-400"/>
            <p class="text-gray-500 dark:text-gray-400 mt-2">Wird gesucht...</p>
          </div>

          <div v-else-if="searchResults.length === 0" class="text-center py-8">
            <Icon name="lucide:search" class=" text-3xl text-gray-300 dark:text-gray-600 mb-2"/>
            <p class="text-gray-500 dark:text-gray-400">Keine Gruppen gefunden</p>
          </div>

          <div v-else class="space-y-2 max-h-48 overflow-y-auto">
            <button
                v-for="result in searchResults"
                :key="result.id"
                class="w-full flex items-center gap-3 p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors text-left"
                :class="selectedGroup?.id === result.id ? 'bg-purple-50 dark:bg-purple-900/20 ring-2 ring-purple-500' : ''"
                @click="selectGroup(result)"
            >
              <div
                  class="w-10 h-10 bg-indigo-100 dark:bg-indigo-900/30 rounded-full flex items-center justify-center shrink-0">
                <Icon name="lucide:users" class=" text-indigo-600 dark:text-indigo-400"/>
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-gray-900 dark:text-white truncate">{{ result.name }}</p>
                <p v-if="result.member_count" class="text-sm text-gray-500 dark:text-gray-400">
                  {{ result.member_count }} Mitglieder
                </p>
              </div>
              <Icon
v-if="selectedGroup?.id === result.id" name="lucide:check"
                    class="text-purple-600 dark:text-purple-400"/>
            </button>
          </div>
        </div>

        <!-- Selected Group Preview -->
        <div
v-if="selectedGroup"
             class="p-4 bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 rounded-lg">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div
                  class="w-10 h-10 bg-indigo-100 dark:bg-indigo-900/30 rounded-full flex items-center justify-center shrink-0">
                <Icon name="lucide:users" class=" text-indigo-600 dark:text-indigo-400"/>
              </div>
              <div>
                <p class="font-medium text-gray-900 dark:text-white">{{ selectedGroup.name }}</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">Als {{ getRoleLabelSingular(selectedRole) }}</p>
              </div>
            </div>
            <button
                class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-neutral-700 rounded-lg transition-colors"
                @click="selectedGroup = null"
            >
              <Icon name="lucide:x"/>
            </button>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div
          class="flex flex-col-reverse sm:flex-row justify-end gap-3 p-6 border-t border-gray-200 dark:border-neutral-700 shrink-0">
        <button
            class="px-5 py-2.5 rounded-lg font-medium transition-all border-2 border-gray-300 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-neutral-700"
            :disabled="adding"
            @click="closeAddDialog"
        >
          Abbrechen
        </button>

        <button
            class="px-5 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            :disabled="!selectedGroup || adding"
            @click="addGroup"
        >
          <Icon :name="adding ? 'lucide:loader-2' : 'lucide:plus'" :class="{ 'animate-spin': adding }"/>
          <span>Hinzufügen</span>
        </button>
      </div>
    </div>
  </div>
</template>
