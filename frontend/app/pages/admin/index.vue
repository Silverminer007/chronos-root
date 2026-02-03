<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {DateTime} from 'luxon';

const NuxtLink = resolveComponent('NuxtLink');

const {fetchUser} = useAuthStore();
await fetchUser();

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
const {data: usersData} = await useFetch<AdminUserResponse>('/api/v2/admin/user/', {params: {size: 10}});

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
  return ((user.firstName[0] ?? '') + (user.lastName[0] ?? '')).toUpperCase();
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
  </div>
</template>
