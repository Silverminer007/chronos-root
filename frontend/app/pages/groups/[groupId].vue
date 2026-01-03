<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {useGroupsStore} from '~/stores/groups';
import {useToast} from 'primevue/usetoast';

const route = useRoute();
const router = useRouter();

const authStore = useAuthStore();
await authStore.fetchUser();

const groupsStore = useGroupsStore();
const toast = useToast();

const groupId = computed(() => Number(route.params.groupId));
const group = computed(() => groupsStore.currentGroup);
const error = ref('');

const showAddMemberDialog = ref(false);
const showLeaveDialog = ref(false);
const showDeleteDialog = ref(false);

const isLastMember = computed(() => {
  return group.value?.members?.length === 1;
});

const existingMemberIds = computed(() => {
  return group.value?.members?.map(m => m.id) || [];
});

const currentUserId = computed(() => authStore.user?.id);

onMounted(async () => {
  try {
    await groupsStore.fetchGroup(groupId.value);
    if (!groupsStore.currentGroup) {
      error.value = 'Gruppe nicht gefunden';
    }
  } catch (err) {
    error.value = 'Gruppe konnte nicht geladen werden';
  }
});

onUnmounted(() => {
  groupsStore.clearCurrentGroup();
});

const handleAddMember = async (userId: number) => {
  try {
    await groupsStore.addMember(groupId.value, userId);
    toast.add({
      severity: 'success',
      summary: 'Mitglied hinzugefügt',
      detail: 'Das Mitglied wurde zur Gruppe hinzugefügt',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Mitglied konnte nicht hinzugefügt werden',
      life: 3000
    });
  }
};

const handleRemoveMember = async (userId: number) => {
  try {
    await groupsStore.removeMember(groupId.value, userId);
    toast.add({
      severity: 'success',
      summary: 'Mitglied entfernt',
      detail: 'Das Mitglied wurde aus der Gruppe entfernt',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Mitglied konnte nicht entfernt werden',
      life: 3000
    });
  }
};

const handleLeaveGroup = async () => {
  if (!currentUserId.value) return;

  try {
    await groupsStore.leaveGroup(groupId.value, currentUserId.value);
    toast.add({
      severity: 'info',
      summary: 'Gruppe verlassen',
      detail: 'Du hast die Gruppe verlassen',
      life: 3000
    });
    router.push('/groups');
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Gruppe konnte nicht verlassen werden',
      life: 3000
    });
  }
  showLeaveDialog.value = false;
};

const handleDeleteGroup = async () => {
  try {
    await groupsStore.deleteGroup(groupId.value);
    toast.add({
      severity: 'success',
      summary: 'Gruppe gelöscht',
      detail: 'Die Gruppe wurde gelöscht',
      life: 3000
    });
    router.push('/groups');
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Gruppe konnte nicht gelöscht werden',
      life: 3000
    });
  }
  showDeleteDialog.value = false;
};

const goBack = () => {
  router.push('/groups');
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <!-- Loading State -->
    <div v-if="groupsStore.loading" class="flex items-center justify-center py-32">
      <div class="text-center">
        <i class="pi animate-spin text-4xl text-blue-600 dark:text-blue-400 mb-4" />
        <p class="text-gray-600 dark:text-gray-400">Gruppe wird geladen...</p>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="container mx-auto px-4 sm:px-6 py-16">
      <div class="max-w-md mx-auto text-center">
        <Icon name="lucide:alert-circle" class=" text-4xl text-red-600 dark:text-red-400 mb-4" />
        <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Gruppe nicht gefunden</h2>
        <p class="text-gray-600 dark:text-gray-400 mb-6">{{ error }}</p>
        <button
            @click="goBack"
            class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 transition-all"
        >
          Zurück zur Übersicht
        </button>
      </div>
    </div>

    <!-- Group Content -->
    <div v-else-if="group" class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Header -->
        <div class="mb-6">
          <button
              @click="goBack"
              class="flex items-center gap-2 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 mb-4 transition-colors"
          >
            <Icon name="lucide:arrow-left" />
            <span>Zurück</span>
          </button>

          <div class="flex items-start justify-between gap-4">
            <div class="flex items-center gap-4">
              <div class="w-16 h-16 bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30 rounded-xl flex items-center justify-center shrink-0">
                <Icon name="lucide:users" class=" text-blue-600 dark:text-blue-400 text-2xl" />
              </div>
              <div>
                <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">{{ group.name }}</h1>
                <p class="text-gray-500 dark:text-gray-400 mt-1">
                  {{ group.members?.length || 0 }} {{ group.members?.length === 1 ? 'Mitglied' : 'Mitglieder' }}
                </p>
              </div>
            </div>

            <button
                @click="showAddMemberDialog = true"
                class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 transition-all shadow-lg items-center gap-2"
            >
              <Icon name="lucide:plus" />
              <span>Mitglied hinzufügen</span>
            </button>
          </div>
        </div>

        <!-- Members Section -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
          <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
            <h2 class="text-lg font-bold text-gray-900 dark:text-white">Mitglieder</h2>
          </div>

          <div class="p-4 space-y-3">
            <GroupMemberCard
                v-for="member in group.members"
                :key="member.id"
                :member="member"
                :can-remove="member.id !== currentUserId && group.members.length > 1"
                @remove="handleRemoveMember"
            />

            <div v-if="!group.members || group.members.length === 0" class="text-center py-8">
              <p class="text-gray-500 dark:text-gray-400">Keine Mitglieder</p>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="mt-6 flex flex-col sm:flex-row gap-3">
          <button
              v-if="isLastMember"
              @click="showDeleteDialog = true"
              class="flex-1 sm:flex-none px-6 py-3 rounded-lg font-medium text-red-600 dark:text-red-400 border-2 border-red-300 dark:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20 transition-all flex items-center justify-center gap-2"
          >
            <Icon name="lucide:trash-2" />
            <span>Gruppe löschen</span>
          </button>
          <button
              v-else
              @click="showLeaveDialog = true"
              class="flex-1 sm:flex-none px-6 py-3 rounded-lg font-medium text-gray-600 dark:text-gray-400 border-2 border-gray-300 dark:border-neutral-600 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-all flex items-center justify-center gap-2"
          >
            <Icon name="lucide:log-out" />
            <span>Gruppe verlassen</span>
          </button>
        </div>
      </div>
    </div>

    <!-- FAB - Add Member (Mobile) -->
    <button
        v-if="group"
        @click="showAddMemberDialog = true"
        class="fixed bottom-6 right-6 w-14 h-14 sm:hidden rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-blue-600 to-indigo-500 hover:from-blue-700 hover:to-indigo-600 transition-all transform hover:scale-110 z-50"
    >
      <Icon name="lucide:plus" class=" text-xl" />
    </button>

    <!-- Add Member Dialog -->
    <AddGroupMemberDialog
        :visible="showAddMemberDialog"
        :existing-member-ids="existingMemberIds"
        @close="showAddMemberDialog = false"
        @add="handleAddMember"
    />

    <!-- Leave Group Dialog -->
    <ConfirmDialog
        :visible="showLeaveDialog"
        title="Gruppe verlassen"
        message="Möchtest du diese Gruppe wirklich verlassen?"
        confirm-text="Verlassen"
        confirm-color="gray"
        @close="showLeaveDialog = false"
        @confirm="handleLeaveGroup"
    />

    <!-- Delete Group Dialog -->
    <ConfirmDialog
        :visible="showDeleteDialog"
        title="Gruppe löschen"
        message="Du bist das letzte Mitglied. Wenn du gehst, wird die Gruppe gelöscht. Fortfahren?"
        confirm-text="Löschen"
        confirm-color="red"
        @close="showDeleteDialog = false"
        @confirm="handleDeleteGroup"
    />

    <Toast />
  </div>
</template>
