<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {DateTime} from 'luxon';
import DatePicker from 'primevue/datepicker';

const {fetchUser} = useAuthStore();
await fetchUser();

interface PushLogEntry {
  id: number;
  user_id: number;
  notification_type: string | null;
  payload: string;
  status_code: number;
  success: boolean;
  error_message: string | null;
  created_at: string;
}

const {data: logEntries, status, error} = await useFetch<PushLogEntry[]>('/api/v2/admin/push/log');

const filterUserId = ref('');
const filterNotificationType = ref('');
const filterStatus = ref<'all' | 'success' | 'error'>('all');
const filterDateFrom = ref<Date | null>(null);
const filterDateTo = ref<Date | null>(null);

const filteredEntries = computed(() => {
  if (!logEntries.value) return [];

  return logEntries.value.filter((entry) => {
    if (filterUserId.value && entry.user_id !== Number(filterUserId.value)) {
      return false;
    }

    if (filterNotificationType.value && entry.notification_type !== filterNotificationType.value) {
      return false;
    }

    if (filterStatus.value === 'success' && !entry.success) {
      return false;
    }
    if (filterStatus.value === 'error' && entry.success) {
      return false;
    }

    if (filterDateFrom.value) {
      const entryDate = DateTime.fromISO(entry.created_at);
      if (entryDate < DateTime.fromJSDate(filterDateFrom.value).startOf('day')) {
        return false;
      }
    }

    if (filterDateTo.value) {
      const entryDate = DateTime.fromISO(entry.created_at);
      if (entryDate > DateTime.fromJSDate(filterDateTo.value).endOf('day')) {
        return false;
      }
    }

    return true;
  });
});

function isSuccess(entry: PushLogEntry): boolean {
  return entry.success;
}

function parsePayload(payload: string): { title: string; body: string } {
  try {
    const parsed = JSON.parse(payload);
    return {title: parsed.title ?? '', body: parsed.body ?? ''};
  } catch {
    return {title: '', body: payload};
  }
}

function formatRelativeTime(dateStr: string): string {
  return DateTime.fromISO(dateStr).setLocale('de').toRelative() ?? '';
}

function getStatusButtonClasses(value: string): string {
  const base = 'px-3 py-1.5 text-sm font-medium rounded-lg transition-all';
  if (filterStatus.value === value) {
    return `${base} bg-purple-600 text-white`;
  }
  return `${base} bg-gray-100 dark:bg-neutral-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-neutral-600`;
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Back Link & Page Header -->
        <div class="mb-6">
          <NuxtLink to="/admin" class="text-sm text-purple-600 dark:text-purple-400 hover:underline mb-2 inline-flex items-center gap-1">
            <Icon name="lucide:arrow-left" class="text-xs" />
            Zurück
          </NuxtLink>
          <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Push-Benachrichtigungen</h1>
          <p class="text-gray-500 dark:text-gray-400 mt-1">Protokoll aller gesendeten Push-Nachrichten</p>
        </div>

        <!-- Filter Card -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6 mb-6">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <!-- User ID -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">User ID</label>
              <InputText v-model="filterUserId" type="number" placeholder="User ID" class="w-full" />
            </div>

            <!-- Notification Type -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Benachrichtigungstyp</label>
              <InputText v-model="filterNotificationType" placeholder="z.B. appointment" class="w-full" />
            </div>

            <!-- Date From -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Von</label>
              <DatePicker v-model="filterDateFrom" date-format="dd.mm.yy" placeholder="Startdatum" class="w-full" />
            </div>

            <!-- Date To -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Bis</label>
              <DatePicker v-model="filterDateTo" date-format="dd.mm.yy" placeholder="Enddatum" :min-date="filterDateFrom ?? undefined" class="w-full" />
            </div>
          </div>

          <!-- Status Toggle -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Status</label>
            <div class="flex gap-1">
              <button :class="getStatusButtonClasses('all')" @click="filterStatus = 'all'">Alle</button>
              <button :class="getStatusButtonClasses('success')" @click="filterStatus = 'success'">Erfolgreich</button>
              <button :class="getStatusButtonClasses('error')" @click="filterStatus = 'error'">Fehlgeschlagen</button>
            </div>
          </div>
        </div>

        <!-- Loading State -->
        <div v-if="status === 'pending'" class="text-center py-16">
          <i class="pi pi-spinner animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400">Wird geladen...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <Icon name="lucide:triangle-alert" class="text-red-600 dark:text-red-400 text-xl" />
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ error.message }}</p>
            </div>
          </div>
        </div>

        <!-- Results List -->
        <div v-else-if="filteredEntries.length > 0" class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
          <ul class="divide-y divide-gray-200 dark:divide-neutral-700">
            <li
                v-for="entry in filteredEntries"
                :key="entry.id"
                class="px-6 py-4"
            >
              <div class="flex items-start gap-3">
                <!-- Status Icon -->
                <div class="flex-shrink-0 mt-0.5">
                  <Icon
                      :name="isSuccess(entry) ? 'lucide:check-circle' : 'lucide:x-circle'"
                      :class="isSuccess(entry) ? 'text-green-500 dark:text-green-400' : 'text-red-500 dark:text-red-400'"
                      class="text-xl"
                  />
                </div>

                <!-- Content -->
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <p class="font-medium text-gray-900 dark:text-white truncate">
                      {{ parsePayload(entry.payload).title || 'Ohne Titel' }}
                    </p>
                    <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300">
                      User {{ entry.user_id }}
                    </span>
                    <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 dark:bg-neutral-700 text-gray-600 dark:text-gray-300">
                      {{ entry.notification_type ?? '—' }}
                    </span>
                    <span
                        class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                        :class="isSuccess(entry) ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300'"
                    >
                      {{ entry.status_code }}
                    </span>
                  </div>
                  <p v-if="parsePayload(entry.payload).body" class="text-sm text-gray-500 dark:text-gray-400 mt-1 truncate">
                    {{ parsePayload(entry.payload).body }}
                  </p>
                  <p v-if="entry.error_message" class="text-sm text-red-600 dark:text-red-400 mt-1">
                    {{ entry.error_message }}
                  </p>
                </div>

                <!-- Timestamp -->
                <span class="text-sm text-gray-500 dark:text-gray-400 flex-shrink-0">
                  {{ formatRelativeTime(entry.created_at) }}
                </span>
              </div>
            </li>
          </ul>
        </div>

        <!-- Empty State -->
        <div v-else class="text-center py-16 px-6">
          <div class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
            <Icon name="lucide:bell-off" class="text-3xl text-purple-600 dark:text-purple-400" />
          </div>
          <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Keine Einträge</h3>
          <p class="text-gray-600 dark:text-gray-400">Es wurden keine Push-Benachrichtigungen gefunden.</p>
        </div>
      </div>
    </div>
  </div>
</template>
