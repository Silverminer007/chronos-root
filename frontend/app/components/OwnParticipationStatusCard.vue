<script setup lang="ts">
import Toast from "primevue/toast";
import type {Appointment} from "~/types";
import {useAppointmentsStore} from "~/stores/appointments";
import {useAuthStore} from "~/stores/auth";
import {useToast} from "primevue/usetoast";

const appointmentStore = useAppointmentsStore();
const authStore = useAuthStore();
const toast = useToast();

const {appointment} = defineProps<{ appointment: Appointment }>();

defineOptions({ inheritAttrs: false })

const updating = ref(false);

// Eigenen Teilnahmestatus aus participants berechnen
const ownParticipationStatus = computed(() => {
  if (!authStore.user?.id || !appointment.participants) return 'PENDING';
  const participation = appointment.participants.find(p => p.user_id === authStore.user!.id);
  return participation?.status || 'PENDING';
});

const updateParticipation = async (action: 'approve' | 'reject') => {
  if (!appointment || !authStore.user?.id || updating.value) return;

  updating.value = true;
  try {
    if (action === 'approve') {
      await appointmentStore.approveAppointment(appointment.id);
    } else {
      await appointmentStore.rejectAppointment(appointment.id);
    }
    toast.add({
      severity: 'success',
      summary: action === 'approve' ? 'Zusage bestätigt' : 'Absage registriert',
      life: 3000
    });
  } catch {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Teilnahmestatus konnte nicht aktualisiert werden',
      life: 3000
    });
  } finally {
    updating.value = false;
  }
};
</script>

<template>
  <Toast />
  <div
      v-bind="$attrs"
      class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
    <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-4">Deine Teilnahme</h3>

    <div class="flex flex-col sm:flex-row gap-3">
      <button
          :disabled="ownParticipationStatus === 'APPROVED' || updating"
          class="flex-1 px-6 py-3 rounded-lg font-medium transition-all flex items-center justify-center gap-2 disabled:cursor-not-allowed"
          :class="ownParticipationStatus === 'APPROVED'
                  ? 'bg-green-600 text-white'
                  : 'border-2 border-green-600 text-green-600 hover:bg-green-50 dark:border-green-500 dark:text-green-500 dark:hover:bg-green-900/20'"
          @click="updateParticipation('approve')"
      >
        <Icon name="lucide:check" />
        <span>Zusagen</span>
      </button>

      <button
          :disabled="ownParticipationStatus === 'REJECTED' || updating"
          class="flex-1 px-6 py-3 rounded-lg font-medium transition-all flex items-center justify-center gap-2 disabled:cursor-not-allowed"
          :class="ownParticipationStatus === 'REJECTED'
                  ? 'bg-red-600 text-white'
                  : 'border-2 border-red-600 text-red-600 hover:bg-red-50 dark:border-red-500 dark:text-red-500 dark:hover:bg-red-900/20'"
          @click="updateParticipation('reject')"
      >
        <Icon name="lucide:x" />
        <span>Absagen</span>
      </button>
    </div>

    <div
v-if="ownParticipationStatus === 'PENDING'"
         class="mt-4 p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
      <div class="flex gap-3">
        <Icon name="lucide:triangle-alert" class=" text-yellow-600 dark:text-yellow-400 mt-0.5" />
        <div class="flex-1">
          <p class="text-sm font-medium text-yellow-900 dark:text-yellow-200">
            Bitte bestätige deine Teilnahme
          </p>
          <p class="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
            Deine Rückmeldung hilft bei der Planung des Termins.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
