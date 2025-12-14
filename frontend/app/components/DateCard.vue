<script setup lang="ts">

import Avatar from "primevue/avatar";
import type {Event} from '~/types';
import {useDateFormatter} from '~/composables/useDateFormatter';
import {useAuthStore} from "~/stores/auth";

const {event} = defineProps<{
  event: Event
}>()

const eventsStore = useEventsStore();

const {formatTimeRange, formatDate} = useDateFormatter();
const {user} = useAuthStore()

const messageDialog = ref<boolean>(false);

async function sendMessage(messageSubject: string, messageBody: string) {
  await eventsStore.sendMessage(event.id, messageSubject, messageBody);
  messageDialog.value = false;
}
</script>

<template>
  <Toast />
  <MessageDialog :visible="messageDialog" :eventTitle="`${event.name} ${formatDate(event.start)}`"
                 :recipientCount="event.attendances.length"
                 @send="sendMessage($event.subject, $event.message)"/>
  <Card>
    <template #title>
      <p v-if="event.status === 'CANCELLED'" class="text-red-500">
        <span class="pi pi-exclamation-triangle"/> Abgesagt</p>
      <p v-if="event.status === 'NOT_ENOUGH_ATTENDEES'" class="text-yellow-500">
        <span class="pi pi-exclamation-triangle"/> Zu wenig Teilnehmende</p>
      <div class="flex flex-row items-center justify-between flex-wrap">
        <div class="flex flex-row gap-2">
          {{ event.name }}
          <p v-if="event.minimal_attendees">({{
              eventsStore.getApprovedAttendances(event).length
            }}/{{
              event.minimal_attendees
            }})</p>
        </div>
        <AvatarGroup>
          <Avatar v-for="attendance in eventsStore.getApprovedAttendances(event)" :key="attendance.id"
                  shape="circle"
                  :label="attendance?.user_name?.charAt(0)" v-tooltip.top="attendance.user_name"/>
        </AvatarGroup>
      </div>
    </template>
    <template #content>
      <div class="flex flex-col gap-2">
        <p>{{ formatTimeRange(event.start, event.end) }}</p>
        <p class="dark:text-gray-400 text-gray-600"> {{ event.description }}</p>
        <div class="flex flex-row items-center justify-between gap-2">
          <Button :disabled="eventsStore.hasApproved(event)"
                  :severity="eventsStore.hasApproved(event) ? 'success' : 'secondary'"
                  @click.stop="eventsStore.updateAttendanceStatus(event.id, 'APPROVED', user?.id)">
            <span class="pi pi-check"></span>
            <p class="not-sm:hidden">Zusagen</p>
          </Button>
          <Button severity="secondary" @click="messageDialog=true"><span class="pi pi-send"></span>
            <p class="not-sm:hidden">Nachricht</p></Button>
          <Button :disabled="eventsStore.hasRejected(event)"
                  :severity="eventsStore.hasRejected(event) ? 'danger' : 'secondary'"
                  @click.stop="eventsStore.updateAttendanceStatus(event.id, 'REJECTED', user?.id)">
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