<template>
  <div v-if="appointmentsStore.loading"
       class="min-h-screen bg-gray-50 dark:bg-neutral-900 flex items-center justify-center">
    <div class="text-center">
      <i class="pi animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
      <p class="text-gray-600 dark:text-gray-400">Termin wird geladen...</p>
    </div>
  </div>

  <div v-else-if="error" class="min-h-screen bg-gray-50 dark:bg-neutral-900 flex items-center justify-center">
    <div class="text-center max-w-md mx-auto p-6">
      <Icon name="lucide:alert-circle" class=" text-4xl text-red-600 dark:text-red-400 mb-4" />
      <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Termin nicht gefunden</h2>
      <p class="text-gray-600 dark:text-gray-400 mb-6">{{ error }}</p>
      <button
          @click="goBack"
          class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600"
      >
        Zurück zur Übersicht
      </button>
    </div>
  </div>

  <div v-else-if="appointment" class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <!-- Header -->
    <AppointmentHeader :appointment="appointment"/>

    <!-- Main Content -->
    <main class="container mx-auto px-4 sm:px-6 py-6 max-w-6xl">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Column: Appointment Details & Actions -->
        <div class="lg:col-span-2 space-y-6">
          <AppointmentDetailsCard :appointment="appointment" class="order-1"/>
          <OwnParticipationStatusCard :appointment="appointment" class="order-2"/>

          <!-- Mobile: Participants Cards here (order-3 & order-4) -->
          <ParticipantsStatusCard :appointment="appointment" class="order-3 lg:hidden"/>
          <div class="order-4 lg:hidden">
            <GroupParticipantsCard :appointment="appointment"/>
          </div>
          <AppointmentResponsibleActions :appointment="appointment" class="order-5 lg:hidden"/>

          <MessagesCard :appointment="appointment" class="order-6"/>
        </div>

        <!-- Right Column: Participants & Groups (Desktop only) -->
        <div class="hidden lg:block lg:col-span-1 space-y-6">
          <ParticipantsStatusCard :appointment="appointment"/>
          <GroupParticipantsCard :appointment="appointment"/>
          <AppointmentResponsibleActions :appointment="appointment"/>
        </div>
      </div>
    </main>
    <Toast/>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted} from 'vue';
import {useRoute} from 'vue-router';
import {useAppointmentsStore} from '~/stores/appointments';

const route = useRoute();
const appointmentsStore = useAppointmentsStore();

const appointmentId = computed(() => Number(route.params.appointmentId));
const appointment = computed(() => appointmentsStore.currentAppointment);
const error = ref('');

const router = useRouter();
const goBack = () => {
  if (router.options.history.state.back) {
    router.back();
  } else {
    navigateTo('/agenda');
  }
};

onMounted(async () => {
  await appointmentsStore.fetchAppointment(appointmentId.value);
  if (appointmentsStore.error) {
    error.value = 'Termin konnte nicht geladen werden';
  } else {
    error.value = '';
  }
});
</script>

<style scoped>
/* Custom scrollbar */
.overflow-y-auto::-webkit-scrollbar {
  width: 6px;
}

.overflow-y-auto::-webkit-scrollbar-track {
  background: transparent;
}

.overflow-y-auto::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.dark .overflow-y-auto::-webkit-scrollbar-thumb {
  background: #475569;
}

.dark .overflow-y-auto::-webkit-scrollbar-thumb:hover {
  background: #64748b;
}
</style>
