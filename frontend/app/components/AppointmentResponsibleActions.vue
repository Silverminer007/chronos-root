<script setup lang="ts">
import Toast from "primevue/toast";
import type {Appointment} from "~/types";
import {useAuthStore} from "~/stores/auth";
import {useToast} from "primevue/usetoast";
import ConfirmDialog from "~/components/ConfirmDialog.vue";

const authStore = useAuthStore();
const appointmentStore = useAppointmentsStore();
const toast = useToast();

const {appointment} = defineProps<{
  appointment: Appointment
}>()

defineOptions({ inheritAttrs: false })

const actionsLoading = ref(false);
const showCancelDialog = ref(false);

const isResponsible = computed(() => {
  if (!appointment || !authStore.user?.id) return false;
  return appointment.participants?.some(
      p => p.user_id === authStore.user?.id && p.role === 'RESPONSIBLE'
  );
});

const handleCancelAppointment = async () => {
  actionsLoading.value = true;
  try {
    await appointmentStore.cancelAppointment(appointment.id);
    toast.add({
      severity: 'success',
      summary: 'Termin abgesagt',
      detail: 'Alle Teilnehmer wurden benachrichtigt',
      life: 3000
    });
  } catch {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Termin konnte nicht abgesagt werden',
      life: 3000
    });
  } finally {
    actionsLoading.value = false;
    showCancelDialog.value = false;
  }
};
</script>

<template>
  <Toast/>
  <div
      v-if="isResponsible"
      v-bind="$attrs"
      class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
    <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-4">Organisator-Aktionen</h3>

    <div class="space-y-3">
      <button
          :disabled="actionsLoading || appointment.status === 'CANCELLED'"
          class="w-full px-6 py-3 rounded-lg font-medium transition-all border-2 border-red-600 dark:border-red-500 text-red-600 dark:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
          @click="showCancelDialog = true"
      >
        <Icon name="lucide:x-circle" />
        <span>Termin absagen</span>
      </button>
    </div>
  </div>
  <!-- Cancel Appointment Dialog -->
  <ConfirmDialog
      :visible="showCancelDialog"
      title="Termin absagen"
      message="Möchtest du diesen Termin wirklich absagen? Alle Teilnehmer werden benachrichtigt."
      confirm-text="Termin absagen"
      confirm-color="red"
      @close="showCancelDialog = false"
      @confirm="handleCancelAppointment"
  />
</template>
