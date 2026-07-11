<template>
  <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700">
    <!-- Header -->
    <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-lg flex items-center justify-center shrink-0">
          <Icon name="lucide:calendar" class=" text-purple-600 dark:text-purple-400" />
        </div>
        <div>
          <h3 class="text-lg font-bold text-gray-900 dark:text-white">
            Terminbenachrichtigungen
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">
            Wähle für welche Rollen du benachrichtigt werden möchtest
          </p>
        </div>
      </div>
    </div>

    <!-- Notification Settings List -->
    <div class="divide-y divide-gray-200 dark:divide-neutral-700">
      <div
v-for="notificationType in notificationTypes"
           :key="notificationType.key"
           class="p-6">
        <!-- Notification Type Header -->
        <div class="flex items-start gap-3 mb-4">
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

        <!-- Role Selection Button Grid -->
        <div class="grid grid-cols-5 gap-2">
          <button
              v-for="option in roleOptions"
              :key="option.value"
              :class="getButtonClasses(option, settings[notificationType.key] === option.value)"
              @click="emit('update', notificationType.key, option.value)"
          >
            <Icon :name="option.icon" :class="getIconClasses(option, settings[notificationType.key] === option.value)" />
            <p class="text-xs font-medium text-gray-900 dark:text-white hidden sm:block">
              {{ option.label }}
            </p>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type {NotificationSettings, AppointmentNotificationSetting} from '~/types';

defineProps<{
  settings: NotificationSettings;
}>();

const emit = defineEmits<{
  update: [key: keyof NotificationSettings, value: string];
}>();

const notificationTypes = [
  {
    key: 'appointment_moved' as keyof NotificationSettings,
    label: 'Termin verschoben',
    description: 'Wenn ein Termin zeitlich verschoben wird',
    icon: 'lucide:calendar-clock'
  },
  {
    key: 'appointment_message' as keyof NotificationSettings,
    label: 'Neue Nachricht',
    description: 'Wenn eine Nachricht in einem Termin gesendet wird',
    icon: 'lucide:mail'
  },
  {
    key: 'appointment_cancelled' as keyof NotificationSettings,
    label: 'Termin abgesagt',
    description: 'Wenn ein Termin abgesagt wird',
    icon: 'lucide:x-circle'
  },
  {
    key: 'appointment_participant_added' as keyof NotificationSettings,
    label: 'Teilnehmer hinzugefügt',
    description: 'Wenn du zu einem Termin eingeladen wirst',
    icon: 'lucide:user-plus'
  },
  {
    key: 'appointment_participation_status_changed' as keyof NotificationSettings,
    label: 'Teilnahmestatus geändert',
    description: 'Wenn jemand zu- oder absagt',
    icon: 'lucide:check-circle'
  },
  {
    key: 'appointment_participation_invalid' as keyof NotificationSettings,
    label: 'Nicht genug Teilnehmer',
    description: 'Wenn ein Termin nicht genug Zusagen hat',
    icon: 'lucide:triangle-alert'
  },
  {
    key: 'appointment_participation_status_pending' as keyof NotificationSettings,
    label: 'Rückmeldung ausstehend',
    description: 'Erinnerung zur Rückmeldung',
    icon: 'lucide:clock'
  },
  {
    key: 'appointment_reminder' as keyof NotificationSettings,
    label: 'Terminerinnerung',
    description: 'Erinnerung vor dem Termin',
    icon: 'lucide:bell'
  }
];

const roleOptions: { value: AppointmentNotificationSetting; label: string; icon: string; color: string }[] = [
  {value: 'DISABLED', label: 'Aus', icon: 'lucide:ban', color: 'gray'},
  {value: 'ALL', label: 'Alle', icon: 'lucide:users', color: 'purple'},
  {value: 'ATTENDANT', label: 'Teiln.', icon: 'lucide:user', color: 'blue'},
  {value: 'HELPER', label: 'Helfer', icon: 'lucide:wrench', color: 'green'},
  {value: 'RESPONSIBLE', label: 'Orga.', icon: 'lucide:star', color: 'orange'}
];

const getButtonClasses = (option: typeof roleOptions[0], isSelected: boolean) => {
  const baseClasses = 'p-2 sm:p-3 rounded-lg border-2 transition-all flex flex-col items-center gap-1';

  if (isSelected) {
    const colorMap: Record<string, string> = {
      gray: 'border-gray-600 dark:border-gray-400 bg-gray-50 dark:bg-gray-800/50',
      purple: 'border-purple-600 dark:border-purple-400 bg-purple-50 dark:bg-purple-900/20',
      blue: 'border-blue-600 dark:border-blue-400 bg-blue-50 dark:bg-blue-900/20',
      green: 'border-green-600 dark:border-green-400 bg-green-50 dark:bg-green-900/20',
      orange: 'border-orange-600 dark:border-orange-400 bg-orange-50 dark:bg-orange-900/20'
    };
    return `${baseClasses} ${colorMap[option.color]}`;
  }

  return `${baseClasses} border-gray-300 dark:border-neutral-600 hover:border-gray-400 dark:hover:border-neutral-500`;
};

const getIconClasses = (option: typeof roleOptions[0], isSelected: boolean) => {
  const colorMap: Record<string, string> = {
    gray: 'text-gray-600 dark:text-gray-400',
    purple: 'text-purple-600 dark:text-purple-400',
    blue: 'text-blue-600 dark:text-blue-400',
    green: 'text-green-600 dark:text-green-400',
    orange: 'text-orange-600 dark:text-orange-400'
  };
  return isSelected ? colorMap[option.color] : 'text-gray-400 dark:text-gray-500';
};
</script>
