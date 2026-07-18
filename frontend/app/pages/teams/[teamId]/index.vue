<script setup lang="ts">
import {useTeamsStore} from '~/stores/teams';
import {useAuthStore} from '~/stores/auth';
import {useToast} from 'primevue/usetoast';
import type {TeamMember, TeamRole} from '~/types';
import ConfirmDialog from '~/components/ConfirmDialog.vue';

const route = useRoute();
const router = useRouter();
const teamsStore = useTeamsStore();
const authStore = useAuthStore();
const toast = useToast();

const myOidcId = computed(() => authStore.user?.id ?? null);

const teamId = computed(() => Number(route.params.teamId));
const team = computed(() => teamsStore.currentTeam);

const myRole = computed(() => team.value?.members.find(m => m.userId === myOidcId.value)?.role ?? null);
const canManageRoles = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN');

const sortedMembers = computed(() => {
  const members = team.value?.members;
  if (!members) return [];
  return [...members].sort((a, b) => {
    if (a.userId === myOidcId.value) return -1;
    if (b.userId === myOidcId.value) return 1;
    return 0;
  });
});

const pendingRoleChange = ref<{ member: TeamMember; newRole: TeamRole } | null>(null);
const showRoleDialog = ref(false);
const showTransferDialog = ref(false);
const pendingTransfer = ref<TeamMember | null>(null);
const showRemoveDialog = ref(false);
const pendingRemove = ref<TeamMember | null>(null);
const removing = ref(false);

const editingName = ref(false);
const nameInput = ref('');
const savingName = ref(false);

const startEditName = () => {
  nameInput.value = team.value?.name ?? '';
  editingName.value = true;
};

const cancelEditName = () => {
  editingName.value = false;
};

const saveTeamName = async () => {
  if (!nameInput.value.trim() || nameInput.value.trim() === team.value?.name) {
    editingName.value = false;
    return;
  }
  savingName.value = true;
  try {
    await teamsStore.renameTeam(teamId.value, nameInput.value.trim());
    editingName.value = false;
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: teamsStore.error ?? 'Teamname konnte nicht geändert werden', life: 4000});
  } finally {
    savingName.value = false;
  }
};

onMounted(async () => {
  try {
    await teamsStore.fetchTeam(teamId.value);
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: 'Team konnte nicht geladen werden', life: 3000});
  }
});

onUnmounted(() => {
  teamsStore.clearCurrentTeam();
});

const memberDisplayName = (m: TeamMember) => {
  if (m.firstName || m.lastName) return [m.firstName, m.lastName].filter(Boolean).join(' ');
  return m.userId;
};

const roleLabel = (role: TeamRole) => {
  if (role === 'OWNER') return 'Eigentümer';
  if (role === 'ADMIN') return 'Admin';
  return 'Mitglied';
};

const roleBadgeClass = (role: TeamRole) => {
  if (role === 'OWNER') return 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300';
  if (role === 'ADMIN') return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300';
  return 'bg-gray-100 text-gray-600 dark:bg-neutral-700 dark:text-gray-400';
};

const requestRoleChange = (member: TeamMember, newRole: TeamRole) => {
  pendingRoleChange.value = {member, newRole};
  showRoleDialog.value = true;
};

const confirmRoleChange = async () => {
  if (!pendingRoleChange.value) return;
  const {member, newRole} = pendingRoleChange.value;
  try {
    await teamsStore.updateMemberRole(teamId.value, member.userId, newRole);
    toast.add({
      severity: 'success',
      summary: 'Rolle geändert',
      detail: `${memberDisplayName(member)} ist jetzt ${roleLabel(newRole)}`,
      life: 3000
    });
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: teamsStore.error ?? 'Rolle konnte nicht geändert werden', life: 4000});
  } finally {
    showRoleDialog.value = false;
    pendingRoleChange.value = null;
  }
};

const canRemoveMember = (member: TeamMember): boolean => {
  if (!myRole.value || member.userId === myOidcId.value) return false;
  if (member.role === 'OWNER') return false;
  if (myRole.value === 'OWNER') return true;
  return myRole.value === 'ADMIN' && member.role === 'MEMBER';
};

const requestRemove = (member: TeamMember) => {
  pendingRemove.value = member;
  showRemoveDialog.value = true;
};

const confirmRemove = async () => {
  if (!pendingRemove.value) return;
  const target = pendingRemove.value;
  removing.value = true;
  try {
    await teamsStore.removeMember(teamId.value, target.userId);
    toast.add({
      severity: 'success',
      summary: 'Mitglied entfernt',
      detail: `${memberDisplayName(target)} wurde aus dem Team entfernt`,
      life: 3000
    });
    showRemoveDialog.value = false;
    pendingRemove.value = null;
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: teamsStore.error ?? 'Mitglied konnte nicht entfernt werden', life: 4000});
  } finally {
    removing.value = false;
  }
};

const requestTransfer = (member: TeamMember) => {
  pendingTransfer.value = member;
  showTransferDialog.value = true;
};

const confirmTransfer = async () => {
  if (!pendingTransfer.value) return;
  const target = pendingTransfer.value;
  try {
    await teamsStore.transferOwnership(teamId.value, target.userId);
    toast.add({
      severity: 'success',
      summary: 'Eigentümerschaft übertragen',
      detail: `${memberDisplayName(target)} ist jetzt Eigentümer`,
      life: 3000
    });
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: teamsStore.error ?? 'Übertragung fehlgeschlagen', life: 4000});
  } finally {
    showTransferDialog.value = false;
    pendingTransfer.value = null;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <!-- Loading -->
    <div v-if="teamsStore.loading" class="flex items-center justify-center py-32">
      <div class="text-center">
        <i class="pi animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
        <p class="text-gray-600 dark:text-gray-400">Team wird geladen...</p>
      </div>
    </div>

    <!-- Content -->
    <div v-else-if="team" class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Header -->
        <div class="mb-6">
          <button
              class="flex items-center gap-2 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 mb-4 transition-colors"
              @click="router.push('/teams')"
          >
            <Icon name="lucide:arrow-left" />
            <span>Zurück</span>
          </button>

          <div class="flex items-start justify-between gap-4">
            <div class="flex items-center gap-4">
              <div class="w-16 h-16 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center shrink-0">
                <Icon name="lucide:shield-check" class="text-purple-600 dark:text-purple-400 text-2xl" />
              </div>
              <div>
                <!-- Inline name edit -->
                <div v-if="editingName" class="flex items-center gap-2">
                  <input
                      v-model="nameInput"
                      type="text"
                      class="text-2xl sm:text-3xl font-bold bg-transparent border-b-2 border-purple-500 text-gray-900 dark:text-white outline-none w-48 sm:w-64"
                      maxlength="64"
                      autofocus
                      @keyup.enter="saveTeamName"
                      @keyup.escape="cancelEditName"
                  />
                  <button
                      class="p-1.5 rounded-lg text-green-600 dark:text-green-400 hover:bg-green-50 dark:hover:bg-green-900/20 transition-colors disabled:opacity-50"
                      :disabled="savingName"
                      @click="saveTeamName"
                  >
                    <Icon :name="savingName ? 'lucide:loader-2' : 'lucide:check'" :class="{'animate-spin': savingName}" />
                  </button>
                  <button
                      class="p-1.5 rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
                      @click="cancelEditName"
                  >
                    <Icon name="lucide:x" />
                  </button>
                </div>
                <div v-else class="flex items-center gap-2">
                  <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">{{ team.name }}</h1>
                  <button
                      v-if="canManageRoles"
                      class="p-1 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                      title="Teamname ändern"
                      @click="startEditName"
                  >
                    <Icon name="lucide:pencil" class="text-base" />
                  </button>
                </div>
                <p class="text-gray-500 dark:text-gray-400 mt-1">
                  {{ team.members?.length || 0 }}
                  {{ (team.members?.length || 0) === 1 ? 'Mitglied' : 'Mitglieder' }}
                </p>
              </div>
            </div>

            <NuxtLink
                :to="`/teams/${team.id}/invites`"
                class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg items-center gap-2"
            >
              <Icon name="lucide:link" />
              <span>Einladungslinks</span>
            </NuxtLink>
          </div>
        </div>

        <!-- Members -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
          <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
            <h2 class="text-lg font-bold text-gray-900 dark:text-white">Mitglieder</h2>
          </div>

          <div class="divide-y divide-gray-100 dark:divide-neutral-700">
            <div
                v-for="member in sortedMembers"
                :key="member.userId"
                class="flex items-center justify-between gap-4 px-6 py-4"
            >
              <!-- Member info -->
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-10 h-10 rounded-full bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center shrink-0">
                  <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
                    {{ member.firstName?.charAt(0) || '?' }}{{ member.lastName?.charAt(0) || '' }}
                  </span>
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <p class="font-medium text-gray-900 dark:text-white truncate">{{ memberDisplayName(member) }}</p>
                    <span
                        v-if="member.userId === myOidcId"
                        class="shrink-0 text-xs font-medium px-2 py-0.5 rounded-full bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300"
                    >Du</span>
                  </div>
                  <span class="inline-block text-xs font-medium px-2 py-0.5 rounded-full mt-0.5" :class="roleBadgeClass(member.role)">
                    {{ roleLabel(member.role) }}
                  </span>
                </div>
              </div>

              <!-- Role actions — only visible to OWNERs/ADMINs and not on their own row -->
              <div v-if="canManageRoles && member.userId !== myOidcId" class="flex items-center gap-2 shrink-0">
                <template v-if="member.role === 'MEMBER'">
                  <button
                      class="text-xs px-3 py-1.5 rounded-lg font-medium text-blue-700 dark:text-blue-300 border border-blue-300 dark:border-blue-700 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors"
                      @click="requestRoleChange(member, 'ADMIN')"
                  >
                    Zu Admin
                  </button>
                </template>
                <template v-else-if="member.role === 'ADMIN'">
                  <button
                      class="text-xs px-3 py-1.5 rounded-lg font-medium text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-neutral-600 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors"
                      @click="requestRoleChange(member, 'MEMBER')"
                  >
                    Zu Mitglied
                  </button>
                  <button
                      class="text-xs px-3 py-1.5 rounded-lg font-medium text-purple-700 dark:text-purple-300 border border-purple-300 dark:border-purple-700 hover:bg-purple-50 dark:hover:bg-purple-900/20 transition-colors"
                      @click="requestTransfer(member)"
                  >
                    Eigentümer
                  </button>
                </template>
                <button
                    v-if="canRemoveMember(member)"
                    class="p-1.5 rounded-lg text-gray-400 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                    title="Mitglied entfernen"
                    @click="requestRemove(member)"
                >
                  <Icon name="lucide:user-minus" />
                </button>
              </div>
            </div>

            <div v-if="!team.members || team.members.length === 0" class="text-center py-8">
              <p class="text-gray-500 dark:text-gray-400">Keine Mitglieder</p>
            </div>
          </div>
        </div>

        <!-- Invite link shortcut (mobile) -->
        <div class="mt-4 sm:hidden">
          <NuxtLink
              :to="`/teams/${team.id}/invites`"
              class="flex items-center justify-center gap-2 w-full px-4 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg"
          >
            <Icon name="lucide:link" />
            <span>Einladungslinks verwalten</span>
          </NuxtLink>
        </div>
      </div>
    </div>

    <!-- Not found -->
    <div v-else class="container mx-auto px-4 sm:px-6 py-16">
      <div class="max-w-md mx-auto text-center">
        <Icon name="lucide:alert-circle" class="text-4xl text-red-600 dark:text-red-400 mb-4" />
        <h2 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Team nicht gefunden</h2>
        <button
            class="mt-4 px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 transition-all"
            @click="router.push('/teams')"
        >
          Zurück zur Übersicht
        </button>
      </div>
    </div>

    <!-- Role Change Confirm Dialog -->
    <ConfirmDialog
        :visible="showRoleDialog"
        title="Rolle ändern"
        :message="pendingRoleChange
          ? `${memberDisplayName(pendingRoleChange.member)} wird zu ${roleLabel(pendingRoleChange.newRole)} geändert. Fortfahren?`
          : ''"
        confirm-text="Ändern"
        @close="showRoleDialog = false; pendingRoleChange = null"
        @confirm="confirmRoleChange"
    />

    <!-- Remove Member Confirm Dialog -->
    <ConfirmDialog
        :visible="showRemoveDialog"
        title="Mitglied entfernen"
        :message="pendingRemove
          ? `${memberDisplayName(pendingRemove)} wirklich aus dem Team entfernen?`
          : ''"
        :warnings="[
          'Der Benutzer verliert Zugriff auf alle Gruppen dieses Teams.',
          'Der Benutzer verliert Zugriff auf alle Termine, zu denen er/sie durch dieses Team eingeladen wurde.'
        ]"
        :loading="removing"
        confirm-text="Entfernen"
        confirm-color="red"
        @close="showRemoveDialog = false; pendingRemove = null"
        @confirm="confirmRemove"
    />

    <!-- Transfer Ownership Confirm Dialog -->
    <ConfirmDialog
        :visible="showTransferDialog"
        title="Eigentümerschaft übertragen"
        :message="pendingTransfer
          ? `Möchtest du ${memberDisplayName(pendingTransfer)} zum neuen Eigentümer machen? Du wirst danach Admin sein.`
          : ''"
        confirm-text="Übertragen"
        confirm-color="red"
        @close="showTransferDialog = false; pendingTransfer = null"
        @confirm="confirmTransfer"
    />

    <Toast />
  </div>
</template>
