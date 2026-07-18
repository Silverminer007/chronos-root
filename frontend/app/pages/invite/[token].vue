<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';

const route = useRoute();
const token = computed(() => route.params.token as string);

const authStore = useAuthStore();
const isLoggedIn = ref(false);

const joining = ref(false);
const joined = ref(false);
const error = ref('');

onMounted(async () => {
  isLoggedIn.value = await authStore.checkSession();
});

const handleJoin = async () => {
  joining.value = true;
  error.value = '';
  try {
    await $fetch(`/api/v2/invite/${token.value}`, {method: 'POST'});
    joined.value = true;
    setTimeout(() => navigateTo('/teams'), 2000);
  } catch (err: unknown) {
    if (err && typeof err === 'object' && 'status' in err && (err as {status: number}).status === 410) {
      error.value = 'Dieser Einladungslink ist abgelaufen oder wurde bereits verwendet.';
    } else {
      error.value = 'Der Link ist ungültig oder du bist bereits Mitglied.';
    }
  } finally {
    joining.value = false;
  }
};

const loginUrl = computed(() => {
  const returnPath = `/invite/${token.value}`;
  return `/api/auth/login?returnTo=${encodeURIComponent(returnPath)}`;
});
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900 flex items-center justify-center p-4">
    <div class="w-full max-w-sm bg-white dark:bg-neutral-800 rounded-2xl shadow-xl border border-gray-200 dark:border-neutral-700 p-8 text-center">
      <!-- Success state -->
      <template v-if="joined">
        <div class="w-16 h-16 bg-green-100 dark:bg-green-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
          <Icon name="lucide:check" class="text-3xl text-green-600 dark:text-green-400" />
        </div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Beigetreten!</h1>
        <p class="text-gray-600 dark:text-gray-400">Du wirst gleich weitergeleitet...</p>
      </template>

      <!-- Error state -->
      <template v-else-if="error">
        <div class="w-16 h-16 bg-red-100 dark:bg-red-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
          <Icon name="lucide:x" class="text-3xl text-red-600 dark:text-red-400" />
        </div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Link ungültig</h1>
        <p class="text-gray-600 dark:text-gray-400 mb-6">{{ error }}</p>
        <NuxtLink
            to="/teams"
            class="inline-block px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 transition-all"
        >
          Meine Teams
        </NuxtLink>
      </template>

      <!-- Not logged in -->
      <template v-else-if="!isLoggedIn">
        <div class="w-16 h-16 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
          <Icon name="lucide:shield-check" class="text-3xl text-purple-600 dark:text-purple-400" />
        </div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Team-Einladung</h1>
        <p class="text-gray-600 dark:text-gray-400 mb-8">Melde dich an, um dem Team beizutreten.</p>
        <a
            :href="loginUrl"
            class="block w-full px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg"
        >
          Anmelden
        </a>
        <NuxtLink to="/" class="block mt-4 text-sm text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
          Zur Startseite
        </NuxtLink>
      </template>

      <!-- Logged in — default join state -->
      <template v-else>
        <div class="w-16 h-16 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
          <Icon name="lucide:shield-check" class="text-3xl text-purple-600 dark:text-purple-400" />
        </div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Team-Einladung</h1>
        <p class="text-gray-600 dark:text-gray-400 mb-8">Du wurdest eingeladen, einem Team beizutreten.</p>
        <button
            :disabled="joining"
            class="w-full px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg"
            @click="handleJoin"
        >
          {{ joining ? 'Beitreten...' : 'Team beitreten' }}
        </button>
        <NuxtLink to="/agenda" class="block mt-4 text-sm text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors">
          Abbrechen
        </NuxtLink>
      </template>
    </div>
  </div>
</template>
