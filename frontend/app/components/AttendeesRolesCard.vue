<template>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
    <!-- Header -->
    <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-bold text-gray-900 dark:text-white">Rollen & Zuständigkeiten</h3>
        <button
            v-if="isResponsible"
            @click="showAddDialog = true"
            class="p-2 text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-900/20 rounded-lg transition-colors"
        >
          <i class="pi pi-plus"></i>
        </button>
      </div>
    </div>

    <!-- Content -->
    <div class="p-6 space-y-6 max-h-[600px] overflow-y-auto">
      <!-- Responsible Section -->
      <div v-if="responsibleUsers.length > 0 || responsibleGroups.length > 0">
        <h4 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-3 flex items-center gap-2">
          <i class="pi pi-star-fill text-purple-600 dark:text-purple-400"></i>
          Organisatoren
        </h4>
        <div class="space-y-3">
          <!-- Responsible Users -->
          <div
              v-for="userAttendee in responsibleUsers"
              :key="'user-' + userAttendee.user.id"
              class="group"
          >
            <div class="flex items-center justify-between p-3 bg-purple-50 dark:bg-purple-900/20 rounded-lg">
              <div class="flex items-center gap-3 flex-1 min-w-0">
                <div
                    class="w-8 h-8 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
                  <span class="text-purple-600 dark:text-purple-400 font-semibold text-xs">
                    {{ userAttendee.user.first_name.charAt(0).toUpperCase() }}
                  </span>
                </div>
                <span class="font-medium text-gray-900 dark:text-white truncate">{{
                    userAttendee.user.first_name
                  }}</span>
              </div>
              <div class="flex items-center gap-2">
                <button
                    v-if="isResponsible && userAttendee.user.id !== currentUserId"
                    @click="showRoleMenu(userAttendee.user.id, 'user', userAttendee.role)"
                    class="opacity-0 group-hover:opacity-100 p-1.5 text-purple-600 dark:text-purple-400 hover:bg-purple-100 dark:hover:bg-purple-900/30 rounded transition-all"
                >
                  <i class="pi pi-pencil text-xs"></i>
                </button>
                <button
                    v-if="isResponsible && userAttendee.user.id !== currentUserId"
                    @click="removeAttendee(userAttendee.user.id, 'user')"
                    class="opacity-0 group-hover:opacity-100 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-all"
                >
                  <i class="pi pi-times text-xs"></i>
                </button>
              </div>
            </div>
          </div>

          <!-- Responsible Groups -->
          <div
              v-for="groupAttendee in responsibleGroups"
              :key="'group-' + groupAttendee.group.id"
              class="group"
          >
            <div class="border-l-4 border-purple-400 dark:border-purple-500">
              <!-- Group Header -->
              <div
                  class="flex items-center justify-between p-3 bg-purple-50 dark:bg-purple-900/20 rounded-r-lg cursor-pointer hover:bg-purple-100 dark:hover:bg-purple-900/30 transition-colors"
                  @click="toggleGroup(groupAttendee.group.id)"
              >
                <div class="flex items-center gap-3 flex-1 min-w-0">
                  <div
                      class="w-8 h-8 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-lg flex items-center justify-center shrink-0">
                    <i class="pi pi-users text-purple-600 dark:text-purple-400 text-xs"></i>
                  </div>
                  <span class="font-medium text-gray-900 dark:text-white truncate">{{ groupAttendee.group.name }}</span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">({{
                      groupAttendee.group.members.length
                    }})</span>
                </div>
                <div class="flex items-center gap-2">
                  <i :class="expandedGroups.includes(groupAttendee.group.id) ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                     class="text-gray-400 text-xs"></i>
                  <button
                      v-if="isResponsible"
                      @click.stop="showRoleMenu(groupAttendee.group.id, 'group', groupAttendee.role)"
                      class="opacity-0 group-hover:opacity-100 p-1.5 text-purple-600 dark:text-purple-400 hover:bg-purple-100 dark:hover:bg-purple-900/30 rounded transition-all"
                  >
                    <i class="pi pi-pencil text-xs"></i>
                  </button>
                  <button
                      v-if="isResponsible"
                      @click.stop="removeAttendee(groupAttendee.group.id, 'group')"
                      class="opacity-0 group-hover:opacity-100 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-all"
                  >
                    <i class="pi pi-times text-xs"></i>
                  </button>
                </div>
              </div>

              <!-- Group Members -->
              <div v-if="expandedGroups.includes(groupAttendee.group.id)" class="ml-8 mt-2 space-y-2 pb-2">
                <div
                    v-for="member in groupAttendee.group.members"
                    :key="'member-' + member.id"
                    class="flex items-center gap-3 p-2 bg-white dark:bg-neutral-700/50 rounded-lg"
                >
                  <div
                      class="w-6 h-6 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
                    <span class="text-purple-600 dark:text-purple-400 font-semibold text-[10px]">
                      {{ member.first_name.charAt(0).toUpperCase() }}
                    </span>
                  </div>
                  <span class="text-sm text-gray-700 dark:text-gray-300 truncate">{{
                      member.first_name
                    }} {{ member.last_name }}</span>
                </div>
                <div v-if="groupAttendee.group.members.length === 0"
                     class="text-xs text-gray-500 dark:text-gray-400 italic p-2">
                  Keine Mitglieder
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Attendants Section -->
      <div v-if="attendantUsers.length > 0 || attendantGroups.length > 0">
        <h4 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-3 flex items-center gap-2">
          <i class="pi pi-user text-blue-600 dark:text-blue-400"></i>
          Teilnehmer
        </h4>
        <div class="space-y-3">
          <!-- Attendant Users -->
          <div
              v-for="userAttendee in attendantUsers"
              :key="'user-' + userAttendee.user.id"
              class="group"
          >
            <div class="flex items-center justify-between p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
              <div class="flex items-center gap-3 flex-1 min-w-0">
                <div
                    class="w-8 h-8 bg-linear-to-br from-blue-100 to-blue-200 dark:from-blue-900/30 dark:to-blue-800/30 rounded-full flex items-center justify-center shrink-0">
                  <span class="text-blue-600 dark:text-blue-400 font-semibold text-xs">
                    {{ userAttendee.user.first_name.charAt(0).toUpperCase() }}
                  </span>
                </div>
                <span class="font-medium text-gray-900 dark:text-white truncate">{{
                    userAttendee.user.first_name
                  }}</span>
              </div>
              <div class="flex items-center gap-2">
                <button
                    v-if="isResponsible"
                    @click="showRoleMenu(userAttendee.user.id, 'user', userAttendee.role)"
                    class="opacity-0 group-hover:opacity-100 p-1.5 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-blue-900/30 rounded transition-all"
                >
                  <i class="pi pi-pencil text-xs"></i>
                </button>
                <button
                    v-if="isResponsible"
                    @click="removeAttendee(userAttendee.user.id, 'user')"
                    class="opacity-0 group-hover:opacity-100 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-all"
                >
                  <i class="pi pi-times text-xs"></i>
                </button>
              </div>
            </div>
          </div>

          <!-- Attendant Groups -->
          <div
              v-for="groupAttendee in attendantGroups"
              :key="'group-' + groupAttendee.group.id"
              class="group"
          >
            <div class="border-l-4 border-blue-400 dark:border-blue-500">
              <div
                  class="flex items-center justify-between p-3 bg-blue-50 dark:bg-blue-900/20 rounded-r-lg cursor-pointer hover:bg-blue-100 dark:hover:bg-blue-900/30 transition-colors"
                  @click="toggleGroup(groupAttendee.group.id)"
              >
                <div class="flex items-center gap-3 flex-1 min-w-0">
                  <div
                      class="w-8 h-8 bg-linear-to-br from-blue-100 to-blue-200 dark:from-blue-900/30 dark:to-blue-800/30 rounded-lg flex items-center justify-center shrink-0">
                    <i class="pi pi-users text-blue-600 dark:text-blue-400 text-xs"></i>
                  </div>
                  <span class="font-medium text-gray-900 dark:text-white truncate">{{ groupAttendee.group.name }}</span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">({{
                      groupAttendee.group.members.length
                    }})</span>
                </div>
                <div class="flex items-center gap-2">
                  <i :class="expandedGroups.includes(groupAttendee.group.id) ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                     class="text-gray-400 text-xs"></i>
                  <button
                      v-if="isResponsible"
                      @click.stop="showRoleMenu(groupAttendee.group.id, 'group', groupAttendee.role)"
                      class="opacity-0 group-hover:opacity-100 p-1.5 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-blue-900/30 rounded transition-all"
                  >
                    <i class="pi pi-pencil text-xs"></i>
                  </button>
                  <button
                      v-if="isResponsible"
                      @click.stop="removeAttendee(groupAttendee.group.id, 'group')"
                      class="opacity-0 group-hover:opacity-100 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-all"
                  >
                    <i class="pi pi-times text-xs"></i>
                  </button>
                </div>
              </div>

              <div v-if="expandedGroups.includes(groupAttendee.group.id)" class="ml-8 mt-2 space-y-2 pb-2">
                <div
                    v-for="member in groupAttendee.group.members"
                    :key="'member-' + member.id"
                    class="flex items-center gap-3 p-2 bg-white dark:bg-neutral-700/50 rounded-lg"
                >
                  <div
                      class="w-6 h-6 bg-linear-to-br from-blue-100 to-blue-200 dark:from-blue-900/30 dark:to-blue-800/30 rounded-full flex items-center justify-center shrink-0">
                    <span class="text-blue-600 dark:text-blue-400 font-semibold text-[10px]">
                      {{ member.first_name.charAt(0).toUpperCase() }}
                    </span>
                  </div>
                  <span class="text-sm text-gray-700 dark:text-gray-300 truncate">{{
                      member.first_name
                    }} {{ member.last_name }}</span>
                </div>
                <div v-if="groupAttendee.group.members.length === 0"
                     class="text-xs text-gray-500 dark:text-gray-400 italic p-2">
                  Keine Mitglieder
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Guests Section -->
      <div v-if="guestUsers.length > 0 || guestGroups.length > 0">
        <h4 class="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase mb-3 flex items-center gap-2">
          <i class="pi pi-eye text-gray-600 dark:text-gray-400"></i>
          Gäste
        </h4>
        <div class="space-y-3">
          <!-- Guest Users -->
          <div
              v-for="userAttendee in guestUsers"
              :key="'user-' + userAttendee.user.id"
              class="group"
          >
            <div class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg">
              <div class="flex items-center gap-3 flex-1 min-w-0">
                <div
                    class="w-8 h-8 bg-gray-200 dark:bg-gray-700 rounded-full flex items-center justify-center shrink-0">
                  <span class="text-gray-600 dark:text-gray-400 font-semibold text-xs">
                    {{ userAttendee.user.first_name.charAt(0).toUpperCase() }}
                  </span>
                </div>
                <span class="font-medium text-gray-900 dark:text-white truncate">{{
                    userAttendee.user.first_name
                  }}</span>
              </div>
              <div class="flex items-center gap-2">
                <button
                    v-if="isResponsible"
                    @click="showRoleMenu(userAttendee.user.id, 'user', userAttendee.role)"
                    class="opacity-0 group-hover:opacity-100 p-1.5 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-all"
                >
                  <i class="pi pi-pencil text-xs"></i>
                </button>
                <button
                    v-if="isResponsible"
                    @click="removeAttendee(userAttendee.user.id, 'user')"
                    class="opacity-0 group-hover:opacity-100 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-all"
                >
                  <i class="pi pi-times text-xs"></i>
                </button>
              </div>
            </div>
          </div>

          <!-- Guest Groups -->
          <div
              v-for="groupAttendee in guestGroups"
              :key="'group-' + groupAttendee.group.id"
              class="group"
          >
            <div class="border-l-4 border-gray-400 dark:border-gray-500">
              <div
                  class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800/50 rounded-r-lg cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700/50 transition-colors"
                  @click="toggleGroup(groupAttendee.group.id)"
              >
                <div class="flex items-center gap-3 flex-1 min-w-0">
                  <div
                      class="w-8 h-8 bg-gray-200 dark:bg-gray-700 rounded-lg flex items-center justify-center shrink-0">
                    <i class="pi pi-users text-gray-600 dark:text-gray-400 text-xs"></i>
                  </div>
                  <span class="font-medium text-gray-900 dark:text-white truncate">{{ groupAttendee.group.name }}</span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">({{ groupAttendee.group.members.length }})</span>
                </div>
                <div class="flex items-center gap-2">
                  <i :class="expandedGroups.includes(groupAttendee.group.id) ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                     class="text-gray-400 text-xs"></i>
                  <button
                      v-if="isResponsible"
                      @click.stop="showRoleMenu(groupAttendee.group.id, 'group', groupAttendee.role)"
                      class="opacity-0 group-hover:opacity-100 p-1.5 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded transition-all"
                  >
                    <i class="pi pi-pencil text-xs"></i>
                  </button>
                  <button
                      v-if="isResponsible"
                      @click.stop="removeAttendee(groupAttendee.group.id, 'group')"
                      class="opacity-0 group-hover:opacity-100 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-all"
                  >
                    <i class="pi pi-times text-xs"></i>
                  </button>
                </div>
              </div>

              <div v-if="expandedGroups.includes(groupAttendee.group.id)" class="ml-8 mt-2 space-y-2 pb-2">
                <div
                    v-for="member in groupAttendee.group.members"
                    :key="'member-' + member.id"
                    class="flex items-center gap-3 p-2 bg-white dark:bg-neutral-700/50 rounded-lg"
                >
                  <div
                      class="w-6 h-6 bg-gray-200 dark:bg-gray-700 rounded-full flex items-center justify-center shrink-0">
                    <span class="text-gray-600 dark:text-gray-400 font-semibold text-[10px]">
                      {{ member.first_name.charAt(0).toUpperCase() }}
                    </span>
                  </div>
                  <span class="text-sm text-gray-700 dark:text-gray-300 truncate">{{
                      member.first_name
                    }} {{ member.last_name }}</span>
                </div>
                <div v-if="groupAttendee.group.members.length === 0"
                     class="text-xs text-gray-500 dark:text-gray-400 italic p-2">
                  Keine Mitglieder
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-if="!hasAnyAttendees" class="text-center py-12">
        <i class="pi pi-users text-4xl text-gray-300 dark:text-gray-600 mb-4"></i>
        <p class="text-gray-500 dark:text-gray-400 mb-4">Noch keine Teilnehmer zugewiesen</p>
        <button
            v-if="isResponsible"
            @click="showAddDialog = true"
            class="px-4 py-2 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600"
        >
          <i class="pi pi-plus mr-2"></i>
          Teilnehmer hinzufügen
        </button>
      </div>
    </div>

    <!-- Add Attendee Dialog -->
    <AddAttendeeDialog
        :visible="showAddDialog"
        @close="showAddDialog = false"
        @add="handleAddAttendee"
    />

    <!-- Role Change Menu -->
    <Menu ref="roleMenu" :model="roleMenuItems" :popup="true"/>
  </div>
</template>

<script setup lang="ts">
import {ref, computed} from 'vue';
import {useAuthStore} from '~/stores/auth';
import {useEventsStore} from '~/stores/events';
import {useToast} from 'primevue/usetoast';
import type {Event, GroupAttendee, UserAttendee} from '~/types';
import Menu from 'primevue/menu';

interface Props {
  event: Event;
}

const {event} = defineProps<Props>();

const authStore = useAuthStore();
const eventStore = useEventsStore();
const toast = useToast();

const showAddDialog = ref(false);
const expandedGroups = ref<number[]>([]);
const roleMenu = ref();
const selectedAttendeeId = ref<number | null>(null);
const selectedAttendeeType = ref<'user' | 'group'>('user');

const currentUserId = computed(() => authStore.user?.id);

const isResponsible = computed(() => {
  if (!event || !currentUserId.value) return false;
  return event.userAttendees?.some(
      ua => ua.user.id === currentUserId.value && ua.role === 'RESPONSIBLE'
  );
});

// Filter users by role
const responsibleUsers: Ref<UserAttendee[]> = computed(() =>
    event.userAttendees?.filter(ua => ua.role === 'RESPONSIBLE') || []
);

const attendantUsers: Ref<UserAttendee[]> = computed(() =>
    event.userAttendees?.filter(ua => ua.role === 'ATTENDANT') || []
);

const guestUsers: Ref<UserAttendee[]> = computed(() =>
    event.userAttendees?.filter(ua => ua.role === 'GUEST') || []
);

// Filter groups by role
const responsibleGroups: Ref<GroupAttendee[]> = computed(() =>
    event.groupAttendees?.filter(ga => ga.role === 'RESPONSIBLE') || []
);

const attendantGroups: Ref<GroupAttendee[]> = computed(() =>
    event.groupAttendees?.filter(ga => ga.role === 'ATTENDANT') || []
);

const guestGroups: Ref<GroupAttendee[]> = computed(() =>
    event.groupAttendees?.filter(ga => ga.role === 'GUEST') || []
);

const hasAnyAttendees = computed(() => {
  return (event.userAttendees?.length || 0) > 0 ||
      (event.groupAttendees?.length || 0) > 0;
});

// Role menu items
const roleMenuItems = computed(() => [
  {
    label: 'Organisator',
    icon: 'pi pi-star-fill',
    command: () => changeRole('RESPONSIBLE')
  },
  {
    label: 'Teilnehmer',
    icon: 'pi pi-user',
    command: () => changeRole('ATTENDANT')
  },
  {
    label: 'Gast',
    icon: 'pi pi-eye',
    command: () => changeRole('GUEST')
  }
]);

const toggleGroup = (groupId: number) => {
  const index = expandedGroups.value.indexOf(groupId);
  if (index > -1) {
    expandedGroups.value.splice(index, 1);
  } else {
    expandedGroups.value.push(groupId);
  }
};

const showRoleMenu = (id: number, type: 'user' | 'group', currentRole: string) => {
  selectedAttendeeId.value = id;
  selectedAttendeeType.value = type;
  roleMenu.value.toggle(event);
};

const changeRole = async (newRole: 'ATTENDANT' | 'RESPONSIBLE' | 'GUEST') => {
  if (!selectedAttendeeId.value) return;

  try {
    if (selectedAttendeeType.value === 'user') {
      await eventStore.updateUserAttendeeRole(event.id, selectedAttendeeId.value, newRole);
    } else {
      await eventStore.updateGroupAttendeeRole(event.id, selectedAttendeeId.value, newRole);
    }

    toast.add({
      severity: 'success',
      summary: 'Rolle aktualisiert',
      life: 3000
    });

    // Reload event
    await eventStore.getEventById(event.id);
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Rolle konnte nicht aktualisiert werden',
      life: 3000
    });
  }
};

const removeAttendee = async (id: number, type: 'user' | 'group') => {
  try {
    if (type === 'user') {
      await eventStore.removeUserAttendee(event.id, id);
    } else {
      await eventStore.removeGroupAttendee(event.id, id);
    }

    toast.add({
      severity: 'success',
      summary: type === 'user' ? 'Person entfernt' : 'Gruppe entfernt',
      life: 3000
    });

    // Reload event
    await eventStore.getEventById(event.id);
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Teilnehmer konnte nicht entfernt werden',
      life: 3000
    });
  }
};

const handleAddAttendee = async (data: {
  type: 'user' | 'group';
  id: number;
  role: 'ATTENDANT' | 'RESPONSIBLE' | 'GUEST'
}) => {
  try {
    if (data.type === 'user') {
      await eventStore.addUserAttendee(event.id, data.id, data.role);
    } else {
      await eventStore.addGroupAttendee(event.id, data.id, data.role);
    }

    toast.add({
      severity: 'success',
      summary: data.type === 'user' ? 'Person hinzugefügt' : 'Gruppe hinzugefügt',
      life: 3000
    });

    // Reload event
    await eventStore.getEventById(event.id);
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Teilnehmer konnte nicht hinzugefügt werden',
      life: 3000
    });
  }
};
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