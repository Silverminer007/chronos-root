<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {useSettingsStore} from '~/stores/settings';
import {useToast} from 'primevue/usetoast';
import {usePush} from '~/composables/usePush';

const {fetchUser} = useAuthStore();
await fetchUser();

const settingsStore = useSettingsStore();
const toast = useToast();
const {permission, subscribe, markAsked} = usePush();

const pushEnabled = ref(false);

onMounted(async () => {
  await settingsStore.fetchSettings();

  if (import.meta.client) {
    pushEnabled.value = Notification.permission === 'granted' &&
        localStorage.getItem('pushPromptAnswer') === 'granted';
  }
});

const handleTogglePush = async () => {
  if (!pushEnabled.value) {
    try {
      const result = await Notification.requestPermission();
      if (result === 'granted') {
        await subscribe();
        markAsked('granted');
        pushEnabled.value = true;
        toast.add({
          severity: 'success',
          summary: 'Push-Benachrichtigungen aktiviert',
          life: 3000
        });
      } else {
        markAsked('denied');
        toast.add({
          severity: 'warn',
          summary: 'Berechtigung verweigert',
          detail: 'Du kannst die Berechtigung in den Browsereinstellungen ändern.',
          life: 5000
        });
      }
    } catch (err) {
      toast.add({
        severity: 'error',
        summary: 'Fehler',
        detail: 'Push-Benachrichtigungen konnten nicht aktiviert werden',
        life: 3000
      });
    }
  } else {
    markAsked('denied');
    pushEnabled.value = false;
    toast.add({
      severity: 'info',
      summary: 'Push-Benachrichtigungen deaktiviert',
      life: 3000
    });
  }
};

const handleSave = async () => {
  try {
    await settingsStore.saveSettings();
    toast.add({
      severity: 'success',
      summary: 'Einstellungen gespeichert',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler beim Speichern',
      detail: 'Bitte versuche es erneut',
      life: 3000
    });
  }
};

const handleUpdateSetting = (key: keyof typeof settingsStore.settings, value: string) => {
  settingsStore.updateSetting(key, value);
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader/>

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Page Header -->
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">
              Einstellungen
            </h1>
            <p class="text-gray-500 dark:text-gray-400 mt-1">
              Verwalte deine Benachrichtigungseinstellungen
            </p>
          </div>
          <button
              @click="handleSave"
              :disabled="settingsStore.saving"
              class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Icon :name="settingsStore.saving ? 'lucide:loader-2' : 'lucide:save'" :class="{ 'animate-spin': settingsStore.saving }" />
            <span>Speichern</span>
          </button>
        </div>

        <!-- Loading State -->
        <div v-if="settingsStore.loading" class="text-center py-16">
          <Icon name="lucide:loader-2" class="animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400">Wird geladen...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="settingsStore.error && !settingsStore.settings"
             class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <Icon name="lucide:triangle-alert" class=" text-red-600 dark:text-red-400 text-xl" />
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ settingsStore.error }}</p>
            </div>
          </div>
        </div>

        <!-- Settings Content -->
        <div v-else class="space-y-6">
          <!-- Push Notifications Card -->
          <NotificationPushCard
              :enabled="pushEnabled"
              :permission="permission"
              @toggle="handleTogglePush"
          />

          <!-- Appointment Notifications Card -->
          <NotificationAppointmentSettingsCard
              v-if="settingsStore.settings"
              :settings="settingsStore.settings"
              @update="handleUpdateSetting"
          />

          <!-- Group Notifications Card -->
          <NotificationGroupSettingsCard
              v-if="settingsStore.settings"
              :settings="settingsStore.settings"
              @update="handleUpdateSetting"
          />
        </div>
      </div>
    </div>

    <!-- Mobile Save Button (FAB style) -->
    <button
        @click="handleSave"
        :disabled="settingsStore.saving"
        class="fixed bottom-6 right-6 w-14 h-14 sm:hidden rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all transform hover:scale-110 z-50 disabled:opacity-50"
    >
      <Icon :name="settingsStore.saving ? 'lucide:loader-2' : 'lucide:save'" class="text-xl" :class="{ 'animate-spin': settingsStore.saving }" />
    </button>

    <Toast/>
  </div>
</template>
