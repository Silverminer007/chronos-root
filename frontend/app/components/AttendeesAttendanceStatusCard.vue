<script setup lang="ts">
import type {Attendance, Event} from '~/types';

const {event} = defineProps<{
  event: Event;
}>();

interface CombinedAttendee extends Attendance {
  role?: 'ATTENDANT' | 'RESPONSIBLE' | 'GUEST';
  name: string;
}

const approvedCount = computed(() => {
  if (!event) return 0;
  return event.attendances.filter(a => a.status === 'APPROVED').length;
});

const sortedAttendees = computed(() => {
  if (!event) return [];

  const combined: CombinedAttendee[] = event.attendances.map(attendance => {
    const userAttendee = event.userAttendees?.find(ua => ua.user.id === attendance.user_id);
    const groupAttendees = event.groupAttendees?.filter(ga => !!ga.group.members.find(u => u.id === attendance.user_id));
    let role = userAttendee?.role || "GUEST";
    if(groupAttendees) {
      groupAttendees.forEach(ga => {
        if(role === "GUEST" || role === "ATTENDANT" && ga.role === "RESPONSIBLE") {
          role = ga.role;
        }
      });
    }
    return {
      ...attendance,
      role: role,
      name: attendance.user_name
    };
  });

  const order = {RESPONSIBLE: 0, ATTENDANT: 1, GUEST: 2};
  return combined.sort((a, b) => {
    if (a.role && b.role && order[a.role] !== order[b.role]) {
      return order[a.role] - order[b.role];
    }
    return a.name.localeCompare(b.name);
  });
});


const getRoleLabel = (role: string) => {
  const labels = {
    RESPONSIBLE: 'Organisator',
    ATTENDANT: 'Teilnehmer',
    GUEST: 'Gast'
  };
  return labels[role] || role;
};

const getRoleBadgeClass = (role: string) => {
  const classes = {
    RESPONSIBLE: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    ATTENDANT: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    GUEST: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-400'
  };
  return classes[role] || classes.GUEST;
};

const getStatusLabel = (status: string) => {
  const labels = {
    APPROVED: 'Zugesagt',
    REJECTED: 'Abgesagt',
    PENDING: 'Ausstehend'
  };
  return labels[status] || status;
};

const getStatusIcon = (status: string) => {
  const icons = {
    APPROVED: 'pi pi-check',
    REJECTED: 'pi pi-times',
    PENDING: 'pi pi-clock'
  };
  return icons[status] || '';
};

const getStatusBadgeClass = (status: string) => {
  const classes = {
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
      <h3 class="text-lg font-bold text-gray-900 dark:text-white">Teilnehmer</h3>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
        {{ approvedCount }} / {{ event.attendances.length }} zugesagt
      </p>
      <div class="mt-3 h-2 bg-gray-200 dark:bg-neutral-700 rounded-full overflow-hidden">
        <div
            class="h-full bg-linear-to-r from-purple-600 to-pink-500 transition-all duration-300"
            :style="{ width: `${(approvedCount / Math.max(event.minimal_attendees, event.attendances.length)) * 100}%` }"
        ></div>
      </div>
    </div>

    <div class="divide-y divide-gray-200 dark:divide-neutral-700 max-h-[600px] overflow-y-auto">
      <div
          v-for="attendee in sortedAttendees"
          :key="attendee.id"
          class="p-4"
      >
        <div class="flex items-start gap-3">
          <div
              class="w-10 h-10 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
                    <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
                      {{ attendee.name.charAt(0).toUpperCase() }}
                    </span>
          </div>
          <div class="flex-1 min-w-0">
            <p class="font-medium text-gray-900 dark:text-white truncate">{{ attendee.name }}</p>
            <div class="flex items-center gap-2 mt-1 flex-wrap">
                      <span
                          v-if="attendee.role"
                          class="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium"
                          :class="getRoleBadgeClass(attendee.role)"
                      >
                        {{ getRoleLabel(attendee.role) }}
                      </span>
              <span
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium"
                  :class="getStatusBadgeClass(attendee.status)"
              >
                        <i :class="getStatusIcon(attendee.status)" class="text-xs"></i>
                        {{ getStatusLabel(attendee.status) }}
                      </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>

</style>