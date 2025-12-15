<script setup lang="ts">
import {computed, ref} from "vue";
import type {Event} from "~/types";
import {useAuthStore} from "~/stores/auth";
import {useToast} from "primevue/usetoast";

const authStore = useAuthStore();
const toast = useToast();

const {event} = defineProps<{
  event: Event
}>()

const actionsLoading = ref(false);
const showCancelDialog = ref(false);
const showRecheckDialog = ref(false);

const isResponsible = computed(() => {
  if (!event || !authStore.user?.id) return false;
  return event.userAttendees?.some(
      ua => ua.user.id === authStore.user?.id && ua.role === 'RESPONSIBLE'
  );
});

const handleRequestRecheck = async () => {
  actionsLoading.value = true;
  try {
    // TODO: API call to request recheck
    toast.add({
      severity: 'success',
      summary: 'Abfrage gesendet',
      detail: 'Alle Teilnehmer wurden benachrichtigt',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Abfrage konnte nicht gesendet werden',
      life: 3000
    });
  } finally {
    actionsLoading.value = false;
    showRecheckDialog.value = false;
  }
};

const handleCancelEvent = async () => {
  actionsLoading.value = true;
  try {
    // TODO: API call to cancel event
    toast.add({
      severity: 'success',
      summary: 'Event abgesagt',
      detail: 'Alle Teilnehmer wurden benachrichtigt',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Event konnte nicht abgesagt werden',
      life: 3000
    });
  } finally {
    actionsLoading.value = false;
    showCancelDialog.value = false;
  }
};

const requestRecheck = () => {
  showRecheckDialog.value = true;
};
</script>

<template>
  <div v-if="isResponsible"
       class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6">
    <h3 class="text-lg font-bold text-gray-900 dark:text-white mb-4">Organisator-Aktionen</h3>

    <div class="space-y-3">
      <button
          @click="requestRecheck"
          :disabled="actionsLoading"
          class="w-full px-6 py-3 rounded-lg font-medium transition-all border-2 border-purple-600 dark:border-purple-400 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <i :class="actionsLoading ? 'pi pi-spin pi-spinner' : 'pi pi-refresh'"></i>
        <span>Teilnahme erneut abfragen</span>
      </button>

      <button
          @click="showCancelDialog = true"
          :disabled="actionsLoading || event.status === 'CANCELLED'"
          class="w-full px-6 py-3 rounded-lg font-medium transition-all border-2 border-red-600 dark:border-red-500 text-red-600 dark:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        <i class="pi pi-times-circle"></i>
        <span>Event absagen</span>
      </button>
    </div>
  </div>
  <!-- Cancel Event Dialog -->
  <ConfirmDialog
      :visible="showCancelDialog"
      title="Event absagen"
      message="Möchtest du dieses Event wirklich absagen? Alle Teilnehmer werden benachrichtigt."
      confirm-text="Event absagen"
      confirm-color="red"
      @close="showCancelDialog = false"
      @confirm="handleCancelEvent"
  />

  <!-- Recheck Dialog -->
  <ConfirmDialog
      :visible="showRecheckDialog"
      title="Teilnahme erneut abfragen"
      message="Möchtest du alle Teilnehmer bitten, ihre Teilnahme erneut zu bestätigen? Sie erhalten eine Benachrichtigung."
      confirm-text="Abfrage senden"
      confirm-color="purple"
      @close="showRecheckDialog = false"
      @confirm="handleRequestRecheck"
  />
</template>

<style scoped>

</style>