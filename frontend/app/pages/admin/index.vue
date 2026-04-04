<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {DateTime} from 'luxon';
import {useToast} from 'primevue/usetoast';
import ConfirmDialog from "~/components/ConfirmDialog.vue";

const NuxtLink = resolveComponent('NuxtLink');

const {fetchUser} = useAuthStore();
await fetchUser();

const toast = useToast();
const showSurveyConfirm = ref(false);
const surveyLoading = ref(false);

async function sendSurveyInvitations() {
  surveyLoading.value = true;
  try {
    await $fetch('/api/v2/admin/survey/11111111-1111-1111-1111-111111111111/notify', {method: 'POST'});
    toast.add({severity: 'success', summary: 'Einladungen versendet', life: 3000});
  } catch {
    toast.add({severity: 'error', summary: 'Fehler beim Versenden', detail: 'Bitte versuche es erneut.', life: 4000});
  } finally {
    surveyLoading.value = false;
    showSurveyConfirm.value = false;
  }
}

interface AdminStatistics {
  users: { total: number };
  appointments: { total: number; planned: number; cancelled: number; deleted: number; notEnoughAttendees: number };
  participations: { total: number; pending: number; approved: number; rejected: number };
  groups: { total: number; totalMembers: number };
  friendships: { total: number; pending: number; accepted: number; declined: number };
  messages: { total: number };
  pushSubscriptions: { total: number };
}

interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  createdAt: string;
  lastUpdate: string;
  lastSeen: string;
}

interface AdminUserResponse {
  items: AdminUser[];
  page: number;
  size: number;
  total: number;
}

const {data: statistics, status} = await useFetch<AdminStatistics>('/api/v2/admin/statistics');
const {data: usersData} = await useFetch<AdminUserResponse>('/api/v2/admin/user/', {params: {size: 20}});

const stats = computed(() => [
  {label: 'Registrierte Nutzer', icon: 'lucide:users', value: statistics.value?.users.total ?? 0},
  {label: 'Anstehende Termine', icon: 'lucide:calendar', value: statistics.value?.appointments.planned ?? 0},
  {label: 'Gruppen', icon: 'lucide:folder', value: statistics.value?.groups.total ?? 0},
  {label: 'Freundschaften', icon: 'lucide:heart', value: statistics.value?.friendships.accepted ?? 0},
  {label: 'Nachrichten', icon: 'lucide:message-square', value: statistics.value?.messages.total ?? 0},
  {label: 'Push-Abonnements', icon: 'lucide:bell', value: statistics.value?.pushSubscriptions.total ?? 0, link: '/admin/push'},
]);

const recentLogins = computed(() => usersData.value?.items ?? []);

function getInitials(user: AdminUser): string {
  return user && user.firstName && user.lastName ? ((user?.firstName[0] ?? '') + (user?.lastName[0] ?? '')).toUpperCase() : '';
}

function formatRelativeTime(dateStr: string): string {
  return DateTime.fromISO(dateStr).setLocale('de').toRelative() ?? '';
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Page Header -->
        <div class="mb-6">
          <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Administration</h1>
          <p class="text-gray-500 dark:text-gray-400 mt-1">Übersicht über die Plattform</p>
        </div>

        <!-- Stats Cards -->
        <div class="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
          <component
              :is="stat.link ? NuxtLink : 'div'"
              v-for="stat in stats"
              :key="stat.label"
              :to="stat.link"
              class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6"
              :class="stat.link ? 'cursor-pointer hover:shadow-md hover:border-purple-300 dark:hover:border-purple-700 transition-all' : ''"
          >
            <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mb-3">
              <Icon :name="stat.icon" class="text-xl text-purple-600 dark:text-purple-400" />
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ stat.value }}</p>
            <p class="text-sm text-gray-500 dark:text-gray-400">{{ stat.label }}</p>
          </component>
        </div>

        <!-- Survey -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6 mb-6">
          <div class="flex items-center justify-between gap-4 flex-wrap">
            <div class="flex items-center gap-3">
              <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center shrink-0">
                <Icon name="lucide:clipboard-list" class="text-xl text-purple-600 dark:text-purple-400" />
              </div>
              <div>
                <h2 class="text-lg font-semibold text-gray-900 dark:text-white">Umfrage</h2>
                <p class="text-sm text-gray-500 dark:text-gray-400">Push-Einladungen an alle noch nicht eingeladenen Nutzer senden</p>
              </div>
            </div>
            <button
              @click="showSurveyConfirm = true"
              :disabled="surveyLoading"
              class="flex items-center gap-2 px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 disabled:opacity-60 disabled:cursor-not-allowed transition-all shadow-sm shrink-0"
            >
              <Icon v-if="surveyLoading" name="lucide:loader-circle" class="animate-spin" />
              <Icon v-else name="lucide:send" />
              <span>Umfrage-Einladungen senden</span>
            </button>
          </div>
        </div>

        <!-- Recent Logins -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
          <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white flex items-center gap-2">
              <Icon name="lucide:log-in" class="text-purple-600 dark:text-purple-400" />
              Letzte Anmeldungen
            </h2>
          </div>
          <ul class="divide-y divide-gray-200 dark:divide-neutral-700">
            <li
                v-for="user in recentLogins"
                :key="user.id"
                class="flex items-center gap-4 px-6 py-4"
            >
              <div class="w-10 h-10 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center flex-shrink-0">
                <span class="text-sm font-semibold text-purple-600 dark:text-purple-400">{{ getInitials(user) }}</span>
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-gray-900 dark:text-white truncate">{{ user.firstName }} {{ user.lastName }}</p>
              </div>
              <span class="text-sm text-gray-500 dark:text-gray-400 flex-shrink-0">{{ formatRelativeTime(user.lastSeen) }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :visible="showSurveyConfirm"
      title="Umfrage-Einladungen senden"
      message="Es wird eine Push-Benachrichtigung an alle Nutzer gesendet, die noch nicht eingeladen wurden. Fortfahren?"
      confirm-text="Senden"
      confirm-color="purple"
      @close="showSurveyConfirm = false"
      @confirm="sendSurveyInvitations"
    />

    <Toast />
  </div>
</template>
