<script setup lang="ts">
import {useTeamsStore} from '~/stores/teams';
import {useTeamInvitesStore} from '~/stores/teamInvites';
import {useToast} from 'primevue/usetoast';
import type {TeamInvite, TeamInviteType} from '~/types';
import ConfirmDialog from '~/components/ConfirmDialog.vue';

const route = useRoute();
const router = useRouter();
const teamsStore = useTeamsStore();
const invitesStore = useTeamInvitesStore();
const toast = useToast();

const teamId = computed(() => Number(route.params.teamId));
const team = computed(() => teamsStore.currentTeam);

const showCreateDialog = ref(false);
const inviteType = ref<TeamInviteType>('MULTI_USE');
const targetEmail = ref('');
const expiryHours = ref(168);
const creating = ref(false);

const pendingRevoke = ref<TeamInvite | null>(null);
const showRevokeDialog = ref(false);

const copiedToken = ref<string | null>(null);

onMounted(async () => {
  try {
    if (!teamsStore.currentTeam) await teamsStore.fetchTeam(teamId.value);
    await invitesStore.fetchInvites(teamId.value);
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: 'Einladungen konnten nicht geladen werden', life: 3000});
  }
});

onUnmounted(() => {
  invitesStore.clearInvites();
});

const inviteUrl = (token: string) => `${window.location.origin}/invite/${token}`;

const copyLink = async (invite: TeamInvite) => {
  await navigator.clipboard.writeText(inviteUrl(invite.token));
  copiedToken.value = invite.token;
  setTimeout(() => { copiedToken.value = null; }, 2000);
};

const statusLabel = (invite: TeamInvite) => {
  if (invite.status === 'REVOKED') return 'Widerrufen';
  if (invite.status === 'USED') return 'Verwendet';
  if (invite.expiresAt && new Date(invite.expiresAt) < new Date()) return 'Abgelaufen';
  return 'Aktiv';
};

const statusClass = (invite: TeamInvite) => {
  if (invite.status !== 'ACTIVE' || (invite.expiresAt && new Date(invite.expiresAt) < new Date())) {
    return 'bg-gray-100 text-gray-600 dark:bg-neutral-700 dark:text-gray-400';
  }
  return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300';
};

const formatDate = (iso: string | null) => {
  if (!iso) return '–';
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'});
};

const openCreateDialog = () => {
  inviteType.value = 'MULTI_USE';
  targetEmail.value = '';
  expiryHours.value = 168;
  showCreateDialog.value = true;
};

const handleCreateInvite = async () => {
  creating.value = true;
  try {
    const payload: {type: TeamInviteType; targetEmail?: string; expiryHours?: number} = {type: inviteType.value};
    if (inviteType.value === 'MULTI_USE') payload.expiryHours = expiryHours.value;
    if (inviteType.value === 'SINGLE_USE' && targetEmail.value.trim()) payload.targetEmail = targetEmail.value.trim();

    const invite = await invitesStore.createInvite(teamId.value, payload);
    toast.add({severity: 'success', summary: 'Einladung erstellt', detail: 'Der Link wurde erstellt', life: 3000});
    showCreateDialog.value = false;
    if (invite) await copyLink(invite);
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: 'Einladung konnte nicht erstellt werden', life: 3000});
  } finally {
    creating.value = false;
  }
};

const requestRevoke = (invite: TeamInvite) => {
  pendingRevoke.value = invite;
  showRevokeDialog.value = true;
};

const confirmRevoke = async () => {
  if (!pendingRevoke.value) return;
  try {
    await invitesStore.revokeInvite(teamId.value, pendingRevoke.value.id);
    toast.add({severity: 'info', summary: 'Widerrufen', detail: 'Der Einladungslink wurde widerrufen', life: 3000});
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: 'Widerrufen fehlgeschlagen', life: 3000});
  } finally {
    showRevokeDialog.value = false;
    pendingRevoke.value = null;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Header -->
        <div class="mb-6">
          <button
              class="flex items-center gap-2 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 mb-4 transition-colors"
              @click="router.push(`/teams/${teamId}`)"
          >
            <Icon name="lucide:arrow-left" />
            <span>Zurück zu {{ team?.name || 'Team' }}</span>
          </button>

          <div class="flex items-center justify-between gap-4">
            <div>
              <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">
                {{ team?.name ?? '…' }}
              </h1>
              <p class="text-gray-500 dark:text-gray-400 mt-1">Einladungslinks</p>
            </div>
            <button
                class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg items-center gap-2"
                @click="openCreateDialog"
            >
              <Icon name="lucide:plus" />
              <span>Neuer Link</span>
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="invitesStore.loading" class="text-center py-16">
          <i class="pi animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400">Wird geladen...</p>
        </div>

        <!-- Invites List -->
        <div v-else class="space-y-3">
          <div
              v-for="invite in invitesStore.invites"
              :key="invite.id"
              class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-neutral-700 p-4"
          >
            <div class="flex flex-col sm:flex-row sm:items-center gap-3">
              <!-- Info -->
              <div class="flex-1 min-w-0 space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="text-xs font-medium px-2 py-0.5 rounded-full" :class="statusClass(invite)">
                    {{ statusLabel(invite) }}
                  </span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">
                    {{ invite.type === 'MULTI_USE' ? 'Mehrfach-Link' : 'Einzel-Link' }}
                  </span>
                  <span v-if="invite.type === 'MULTI_USE'" class="text-xs text-gray-500 dark:text-gray-400">
                    · {{ invite.useCount }}× verwendet
                  </span>
                  <span v-if="invite.targetEmail" class="text-xs text-gray-500 dark:text-gray-400">
                    · {{ invite.targetEmail }}
                  </span>
                </div>
                <div class="flex items-center gap-2 text-xs text-gray-400 dark:text-gray-500">
                  <span v-if="invite.expiresAt">Läuft ab: {{ formatDate(invite.expiresAt) }}</span>
                  <span v-else>Kein Ablaufdatum</span>
                </div>
                <p class="text-xs font-mono text-gray-400 dark:text-gray-500 truncate">
                  /invite/{{ invite.token }}
                </p>
              </div>

              <!-- Actions -->
              <div class="flex items-center gap-2 shrink-0">
                <button
                    v-if="invite.status === 'ACTIVE'"
                    class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors"
                    :class="copiedToken === invite.token
                      ? 'border-green-400 text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/20'
                      : 'border-gray-300 dark:border-neutral-600 text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-neutral-700'"
                    @click="copyLink(invite)"
                >
                  <Icon :name="copiedToken === invite.token ? 'lucide:check' : 'lucide:copy'" />
                  {{ copiedToken === invite.token ? 'Kopiert!' : 'Link kopieren' }}
                </button>
                <button
                    v-if="invite.status === 'ACTIVE'"
                    class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-red-300 dark:border-red-700 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                    @click="requestRevoke(invite)"
                >
                  <Icon name="lucide:x" />
                  Widerrufen
                </button>
              </div>
            </div>
          </div>

          <!-- Empty -->
          <div v-if="invitesStore.invites.length === 0" class="text-center py-16 px-6">
            <div class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
              <Icon name="lucide:link" class="text-3xl text-purple-600 dark:text-purple-400" />
            </div>
            <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Keine Einladungslinks</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">Erstelle einen Link, um neue Mitglieder einzuladen.</p>
            <button
                class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 transition-all shadow-lg"
                @click="openCreateDialog"
            >
              <Icon name="lucide:plus" class="mr-2" />
              Ersten Link erstellen
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- FAB (Mobile) -->
    <button
        class="fixed bottom-6 right-6 w-14 h-14 sm:hidden rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all transform hover:scale-110 z-50"
        @click="openCreateDialog"
    >
      <Icon name="lucide:plus" class="text-xl" />
    </button>

    <!-- Create Invite Dialog -->
    <Dialog v-model:visible="showCreateDialog" modal header="Einladungslink erstellen" class="w-full max-w-md mx-4">
      <div class="space-y-4 pt-2">
        <!-- Type selection -->
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Link-Typ</label>
          <div class="grid grid-cols-2 gap-3">
            <button
                class="px-4 py-3 rounded-lg border-2 text-sm font-medium transition-all text-left"
                :class="inviteType === 'MULTI_USE'
                  ? 'border-purple-500 bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-300'
                  : 'border-gray-200 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:border-gray-300'"
                @click="inviteType = 'MULTI_USE'"
            >
              <Icon name="lucide:link" class="mb-1" /><br />
              Mehrfach-Link
              <p class="text-xs font-normal text-gray-500 dark:text-gray-400 mt-0.5">Unbegrenzte Nutzungen</p>
            </button>
            <button
                class="px-4 py-3 rounded-lg border-2 text-sm font-medium transition-all text-left"
                :class="inviteType === 'SINGLE_USE'
                  ? 'border-purple-500 bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-300'
                  : 'border-gray-200 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:border-gray-300'"
                @click="inviteType = 'SINGLE_USE'"
            >
              <Icon name="lucide:user-plus" class="mb-1" /><br />
              Einzel-Link
              <p class="text-xs font-normal text-gray-500 dark:text-gray-400 mt-0.5">Einmalige Nutzung</p>
            </button>
          </div>
        </div>

        <!-- MULTI_USE: expiry -->
        <div v-if="inviteType === 'MULTI_USE'">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Gültigkeit</label>
          <select
              v-model="expiryHours"
              class="w-full px-4 py-2.5 rounded-lg border-2 border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white outline-none focus:border-purple-500 dark:focus:border-purple-400 transition-all"
          >
            <option :value="24">24 Stunden</option>
            <option :value="168">7 Tage</option>
            <option :value="720">30 Tage</option>
          </select>
        </div>

        <!-- SINGLE_USE: optional email -->
        <div v-if="inviteType === 'SINGLE_USE'">
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            E-Mail (optional)
          </label>
          <input
              v-model="targetEmail"
              type="email"
              placeholder="person@beispiel.de"
              class="w-full px-4 py-2.5 rounded-lg border-2 border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
          />
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">Nur zur Kennzeichnung — kein E-Mail-Versand</p>
        </div>

        <div class="flex justify-end gap-3 pt-2">
          <button
              class="px-4 py-2 rounded-lg font-medium text-gray-700 dark:text-gray-300 border border-gray-300 dark:border-neutral-600 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors"
              @click="showCreateDialog = false"
          >
            Abbrechen
          </button>
          <button
              :disabled="creating"
              class="px-4 py-2 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
              @click="handleCreateInvite"
          >
            {{ creating ? 'Wird erstellt...' : 'Erstellen & kopieren' }}
          </button>
        </div>
      </div>
    </Dialog>

    <!-- Revoke Confirm Dialog -->
    <ConfirmDialog
        :visible="showRevokeDialog"
        title="Einladungslink widerrufen"
        message="Dieser Link kann danach nicht mehr zum Beitreten genutzt werden. Fortfahren?"
        confirm-text="Widerrufen"
        confirm-color="red"
        @close="showRevokeDialog = false; pendingRevoke = null"
        @confirm="confirmRevoke"
    />

    <Toast />
  </div>
</template>
