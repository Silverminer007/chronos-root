<template>
  <div v-if="visible" class="fixed inset-0 flex items-center justify-center bg-black/60 z-50 p-4">
    <div class="w-full max-w-md bg-white dark:bg-neutral-800 rounded-xl shadow-2xl transform transition-all">
      <!-- Header -->
      <div class="p-6 border-b border-gray-200 dark:border-neutral-700">
        <div class="flex items-center gap-3">
          <div
              class="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
              :class="getIconBackgroundClass()"
          >
            <Icon :name="getIconName()" class="text-xl" :class="getIconColorClass()" />
          </div>
          <h2 class="text-xl font-bold text-gray-900 dark:text-white">{{ title }}</h2>
        </div>
      </div>

      <!-- Content -->
      <div class="p-6">
        <p class="text-gray-700 dark:text-gray-300">{{ message }}</p>
      </div>

      <!-- Footer -->
      <div class="flex flex-col-reverse sm:flex-row justify-end gap-3 p-6 border-t border-gray-200 dark:border-neutral-700">
        <button
            class="px-5 py-2.5 rounded-lg font-medium transition-all border-2 border-gray-300 dark:border-neutral-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-neutral-700"
            @click="close"
        >
          Abbrechen
        </button>

        <button
            class="px-5 py-2.5 rounded-lg font-medium text-white transition-all shadow-lg flex items-center justify-center gap-2"
            :class="getConfirmButtonClass()"
            @click="confirm"
        >
          <Icon :name="getConfirmIconName()" />
          <span>{{ confirmText }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  visible: boolean;
  title: string;
  message: string;
  confirmText: string;
  confirmColor?: 'red' | 'purple' | 'green';
}

const props = withDefaults(defineProps<Props>(), {
  confirmColor: 'purple'
});

const emit = defineEmits<{
  close: [];
  confirm: [];
}>();

const getIconBackgroundClass = () => {
  const classes = {
    red: 'bg-red-100 dark:bg-red-900/30',
    purple: 'bg-purple-100 dark:bg-purple-900/30',
    green: 'bg-green-100 dark:bg-green-900/30'
  };
  return classes[props.confirmColor];
};

const getIconName = () => {
  const icons = {
    red: 'lucide:triangle-alert',
    purple: 'lucide:circle-help',
    green: 'lucide:check-circle'
  };
  return icons[props.confirmColor];
};

const getIconColorClass = () => {
  const classes = {
    red: 'text-red-600 dark:text-red-400',
    purple: 'text-purple-600 dark:text-purple-400',
    green: 'text-green-600 dark:text-green-400'
  };
  return classes[props.confirmColor];
};

const getConfirmButtonClass = () => {
  const classes = {
    red: 'bg-red-600 hover:bg-red-700 dark:bg-red-500 dark:hover:bg-red-600',
    purple: 'bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 dark:from-purple-500 dark:to-pink-400',
    green: 'bg-green-600 hover:bg-green-700 dark:bg-green-500 dark:hover:bg-green-600'
  };
  return classes[props.confirmColor];
};

const getConfirmIconName = () => {
  const icons = {
    red: 'lucide:x-circle',
    purple: 'lucide:check',
    green: 'lucide:check'
  };
  return icons[props.confirmColor];
};

const close = () => {
  emit('close');
};

const confirm = () => {
  emit('confirm');
};
</script>