<template>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
    <!-- Header -->
    <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30 rounded-lg flex items-center justify-center shrink-0">
          <Icon name="lucide:network" class=" text-blue-600 dark:text-blue-400" />
        </div>
        <div>
          <h3 class="text-lg font-bold text-gray-900 dark:text-white">
            Gruppenbenachrichtigungen
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">
            Benachrichtigungen für Gruppenaktivitäten
          </p>
        </div>
      </div>
    </div>

    <!-- Group Notification Settings -->
    <div class="p-6">
      <div v-for="notificationType in groupNotificationTypes"
           :key="notificationType.key">
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-start gap-3">
            <Icon :name="notificationType.icon" class="text-gray-400 dark:text-gray-500 mt-0.5" />
            <div>
              <p class="font-medium text-gray-900 dark:text-white">
                {{ notificationType.label }}
              </p>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ notificationType.description }}
              </p>
            </div>
          </div>

          <!-- Simple Toggle Grid -->
          <div class="grid grid-cols-2 gap-2 shrink-0">
            <button
                v-for="option in options"
                :key="option.value"
                @click="emit('update', notificationType.key, option.value)"
                class="px-4 py-2 rounded-lg border-2 transition-all flex items-center gap-2"
                :class="settings[notificationType.key] === option.value
                    ? (option.value === 'ENABLED'
                        ? 'border-green-600 dark:border-green-400 bg-green-50 dark:bg-green-900/20'
                        : 'border-gray-600 dark:border-gray-400 bg-gray-50 dark:bg-gray-800/50')
                    : 'border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500'"
            >
              <Icon :name="option.icon"
                  :class="settings[notificationType.key] === option.value
                      ? (option.value === 'ENABLED'
                          ? 'text-green-600 dark:text-green-400'
                          : 'text-gray-600 dark:text-gray-400')
                      : 'text-gray-400 dark:text-gray-500'" />
              <span class="text-sm font-medium text-gray-900 dark:text-white">
                {{ option.label }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type {NotificationSettings, GroupNotificationSetting} from '~/types';

const props = defineProps<{
  settings: NotificationSettings;
}>();

const emit = defineEmits<{
  update: [key: keyof NotificationSettings, value: string];
}>();

const groupNotificationTypes = [
  {
    key: 'group_member_added' as keyof NotificationSettings,
    label: 'Mitglied hinzugefügt',
    description: 'Wenn ein neues Mitglied einer Gruppe beitritt',
    icon: 'lucide:user-plus'
  }
];

const options: { value: GroupNotificationSetting; label: string; icon: string }[] = [
  {value: 'DISABLED', label: 'Aus', icon: 'lucide:ban'},
  {value: 'ENABLED', label: 'An', icon: 'lucide:check'}
];
</script>
