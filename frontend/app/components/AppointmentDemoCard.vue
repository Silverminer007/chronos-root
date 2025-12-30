<script setup lang="ts">
import Toast from "primevue/toast";
import Avatar from "primevue/avatar";
import type {Appointment} from '~/types';
import {useDateFormatter} from '~/composables/useDateFormatter';
import {useAppointmentsStore} from '~/stores/appointments';
import {useAuthStore} from '~/stores/auth';

import { useToast } from 'primevue/usetoast';
const toast = useToast();

const {appointment} = defineProps<{
  appointment: Appointment
}>()

const appointmentsStore = useAppointmentsStore();
const authStore = useAuthStore();

const {formatTimeRange} = useDateFormatter();

// Get approved participants
const approvedParticipants = computed(() =>
    appointmentsStore.getApprovedParticipants(appointment)
);

// Check if current user has approved/rejected
const hasApproved = computed(() =>
    authStore.user ? appointmentsStore.hasApproved(appointment, authStore.user.id) : false
);

const hasRejected = computed(() =>
    authStore.user ? appointmentsStore.hasRejected(appointment, authStore.user.id) : false
);

function setParticipationStatus(status: "APPROVED" | "REJECTED") {
  // Demo: just update local state for display purposes
  if (appointment?.participants?.length > 0) {
    const userParticipant = appointment.participants.find(
        p => p.user_id === authStore.user?.id
    );
    if (userParticipant) {
      userParticipant.status = status;
    }
  }
}

const messageDialog = ref<boolean>(false)

async function sendMessage() {
  await new Promise(resolve => setTimeout(resolve, 500));
  toast.add({severity: 'info', summary: 'Die Nachricht wurde versendet', life: 3000});
  messageDialog.value = false;
}
</script>

<template>
  <Toast/>
  <MessageDialog :visible="messageDialog" :eventTitle="appointment.name"
                 :recipientCount="appointment.participants?.length || 0"
                 @send="sendMessage()"/>
  <Card>
    <template #title>
      <p v-if="appointment.status === 'CANCELLED'" class="text-red-500">
        <span class="pi pi-exclamation-triangle"/> Abgesagt</p>
      <p v-if="appointment.status === 'NOT_ENOUGH_ATTENDEES'" class="text-yellow-500">
        <span class="pi pi-exclamation-triangle"/> Zu wenig Teilnehmende</p>
      <div class="flex flex-row items-center justify-between flex-wrap">
        <div class="flex flex-row gap-2">
          {{ appointment.name }}
          <p v-if="appointment.minimal_attendees">({{
              approvedParticipants.length
            }}/{{
              appointment.minimal_attendees
            }})</p>
        </div>
        <AvatarGroup>
          <Avatar v-for="participant in approvedParticipants" :key="participant.user_id"
                  shape="circle"
                  :label="participant?.name?.charAt(0)" v-tooltip.top="participant.name"/>
        </AvatarGroup>
      </div>
    </template>
    <template #content>
      <div class="flex flex-col gap-2">
        <p>{{ formatTimeRange(appointment.start, appointment.end) }}</p>
        <p class="text-gray-400"> {{ appointment.description }}</p>
        <div class="flex flex-row items-center justify-between gap-2">
          <Button :disabled="hasApproved"
                  :severity="hasApproved ? 'success' : 'secondary'"
                  @click.stop="setParticipationStatus('APPROVED')">
            <span class="pi pi-check"></span>
            <p class="not-sm:hidden">Zusagen</p>
          </Button>
          <Button severity="secondary" @click="messageDialog = true"><span class="pi pi-send"></span>
            <p class="not-sm:hidden">Nachricht</p></Button>
          <Button :disabled="hasRejected"
                  :severity="hasRejected ? 'danger' : 'secondary'"
                  @click.stop="setParticipationStatus('REJECTED')">
            <span class="pi pi-thumbs-down"></span>
            <p class="not-sm:hidden">Absagen</p>
          </Button>
        </div>
      </div>
    </template>
  </Card>
</template>

<style scoped>
.p-button:disabled,
.p-button.p-disabled {
  opacity: 1 !important;
  filter: none !important;
  cursor: not-allowed;
}
</style>
