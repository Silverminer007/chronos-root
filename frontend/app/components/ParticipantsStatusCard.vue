<script setup lang="ts">
import type {Appointment, UserParticipant} from '~/types';
import {useAppointmentsStore} from '~/stores/appointments';
import {useAuthStore} from '~/stores/auth';
import {useToast} from 'primevue/usetoast';

const appointmentsStore = useAppointmentsStore();
const authStore = useAuthStore();
const toast = useToast();

const {appointment} = defineProps<{
  appointment: Appointment;
}>();

const showAddDialog = ref(false);

const isResponsible = computed(() => {
  if (!appointment || !authStore.user?.id) return false;
  return appointment.participants?.some(
      p => p.user_id === authStore.user?.id && p.role === 'RESPONSIBLE'
  );
});

const handleAddParticipant = async (data: { type: 'user' | 'group'; id: number; role: string }) => {
  try {
    if (data.type === 'user') {
      await appointmentsStore.addParticipant(appointment.id, data.id, data.role);
    } else {
      await appointmentsStore.addGroupParticipant(appointment.id, data.id, data.role);
    }
    toast.add({
      severity: 'success',
      summary: 'Teilnehmer hinzugefügt',
      detail: 'Der Teilnehmer wurde erfolgreich hinzugefügt',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Teilnehmer konnte nicht hinzugefügt werden',
      life: 3000
    });
  }
};

const approvedCount = computed(() => {
  if (!appointment?.participants) return 0;
  return appointment.participants.filter(p => p.status === 'APPROVED').length;
});

const totalCount = computed(() => {
  return appointment?.participants?.length || 0;
});

// Teilnehmer nach Rolle sortieren
const sortedParticipants = computed(() => {
  if (!appointment?.participants) return [];

  const order: Record<string, number> = {RESPONSIBLE: 0, ATTENDANT: 1, HELPER: 2, GUEST: 3, NONE: 4};
  return [...appointment.participants].sort((a, b) => {
    const roleOrder = (order[a.role] ?? 4) - (order[b.role] ?? 4);
    if (roleOrder !== 0) return roleOrder;
    return a.name.localeCompare(b.name);
  });
});

const getRoleLabel = (role: string) => {
  const labels: Record<string, string> = {
    RESPONSIBLE: 'Organisator',
    ATTENDANT: 'Teilnehmer',
    HELPER: 'Helfer',
    GUEST: 'Gast',
    NONE: ''
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

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    APPROVED: 'Zugesagt',
    REJECTED: 'Abgesagt',
    PENDING: 'Ausstehend'
  };
  return labels[status] || status;
};

const getStatusIconName = (status: string) => {
  const icons: Record<string, string> = {
    APPROVED: 'lucide:check',
    REJECTED: 'lucide:x',
    PENDING: 'lucide:clock'
  };
  return icons[status] || '';
};

const getStatusBadgeClass = (status: string) => {
  const classes: Record<string, string> = {
    APPROVED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    REJECTED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    PENDING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400'
  };
  return classes[status] || classes.PENDING;
};
</script>

<template>
  <div
      class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
    <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white">Teilnehmer</h3>
        <button
            v-if="isResponsible"
            @click="showAddDialog = true"
            class="w-8 h-8 flex items-center justify-center rounded-lg bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 text-white transition-all shadow-sm"
        >
          <Icon name="lucide:plus" class=" text-sm" />
        </button>
      </div>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
        {{ approvedCount }} / {{ totalCount }} zugesagt
      </p>
      <div class="mt-3 h-2 bg-gray-200 dark:bg-neutral-700 rounded-full overflow-hidden">
        <div
            class="h-full bg-linear-to-r from-purple-600 to-pink-500 transition-all duration-300"
            :style="{ width: `${(approvedCount / Math.max(appointment.minimal_attendees, totalCount, 1)) * 100}%` }"
        ></div>
      </div>
    </div>

    <div class="divide-y divide-gray-200 dark:divide-neutral-700 max-h-[600px] overflow-y-auto">
      <div
          v-for="participant in sortedParticipants"
          :key="participant.user_id"
          class="p-4"
      >
        <div class="flex items-start gap-3">
          <div
              class="w-10 h-10 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
            <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
              {{ participant.name?.charAt(0)?.toUpperCase() || '?' }}
            </span>
          </div>
          <div class="flex-1 min-w-0">
            <p class="font-medium text-gray-900 dark:text-white truncate">{{ participant.name }}</p>
            <div class="flex items-center gap-2 mt-1 flex-wrap">
              <span
                  v-if="participant.role && participant.role !== 'NONE'"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                  :class="getRoleBadgeClass(participant.role)"
              >
                {{ getRoleLabel(participant.role) }}
              </span>
              <span
                  v-if="participant.via_group_name"
                  class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400"
              >
                <Icon name="lucide:users" class=" mr-1 text-xs" />
                {{ participant.via_group_name }}
              </span>
              <span
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium"
                  :class="getStatusBadgeClass(participant.status)"
              >
                <Icon :name="getStatusIconName(participant.status)" class="text-xs" />
                {{ getStatusLabel(participant.status) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <AddParticipantDialog
        :visible="showAddDialog"
        @close="showAddDialog = false"
        @add="handleAddParticipant"
    />
  </div>
</template>

<style scoped>

</style>
