<script setup lang="ts">
import {ref} from "vue";
import {useAuthStore} from "~/stores/auth";
import {useAppointmentsStore} from "~/stores/appointments";
import Menu from 'primevue/menu';

const {search} = useAppointmentsStore()
const {logout, user, authenticated} = useAuthStore()

const searchQuery = ref("");
const menu = ref();

const items = ref([
  {
    items: [
      /*{
        label: 'Profil',
        iconName: 'lucide:user',
        command: () => {
          navigateTo('/profile')
        }
      },*/
      {
        label: 'Freunde',
        iconName: 'lucide:users',
        command: () => {
          navigateTo('/friends')
        }
      },
      {
        label: 'Gruppen',
        iconName: 'lucide:network',
        command: () => {
          navigateTo('/groups')
        }
      },
      {
        label: 'Einstellungen',
        iconName: 'lucide:settings',
        command: () => {
          navigateTo('/settings')
        }
      },
      {
        separator: true
      },
      {
        label: 'Abmelden',
        iconName: 'lucide:log-out',
        command: () => logout(),
      }
    ]
  }
]);

const toggle = (event) => {
  menu.value.toggle(event);
};

const handleSearch = () => {
  search(searchQuery.value);
};
</script>

<template>
  <header class="fixed top-0 left-0 right-0 z-40 bg-white dark:bg-neutral-800 border-b border-gray-200 dark:border-neutral-700 shadow-sm">
    <div class="container mx-auto px-4 sm:px-6">
      <div class="flex items-center justify-between h-16 gap-4">
        <!-- Logo -->
        <NuxtLink to="/agenda" class="flex items-center gap-3 shrink-0">
          <img src="/icons/icon.png" alt="Chronos" class="w-8 h-8 sm:w-10 sm:h-10 rounded-lg" />
          <span class="text-xl sm:text-2xl font-bold bg-linear-to-r from-purple-600 to-pink-500 bg-clip-text text-transparent hidden sm:block">
            Chronos
          </span>
        </NuxtLink>

        <!-- Search Bar -->
        <div class="flex-1 max-w-2xl">
          <div class="relative">
            <Icon name="lucide:search" class=" absolute left-4 top-1/2 -translate-y-1/2 text-gray-400 dark:text-gray-500" />
            <input
                v-model="searchQuery"
                @input="handleSearch"
                type="text"
                placeholder="Termine durchsuchen..."
                class="w-full pl-11 pr-4 py-2.5 rounded-lg border-2 border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:bg-white dark:focus:bg-neutral-600 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
            />
          </div>
        </div>

        <!-- User Menu -->
        <div v-if="authenticated" class="flex items-center gap-3 shrink-0">
          <!-- User Avatar -->
          <button
              @click="toggle"
              class="flex items-center gap-2 p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
              aria-haspopup="true"
              aria-controls="user_menu"
          >
            <div class="w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center">
              <span class="text-purple-600 dark:text-purple-400 font-semibold text-sm">
                {{ user?.first_name?.charAt(0) }}{{ user?.last_name?.charAt(0) }}
              </span>
            </div>
            <span class="hidden md:block font-medium text-gray-900 dark:text-white">
              {{ user?.first_name }}
            </span>
            <Icon name="lucide:chevron-down" class=" text-xs text-gray-400 hidden md:block" />
          </button>
        </div>

        <!-- Login Button (if not authenticated) -->
        <NuxtLink
            v-else
            to="/api/auth/login"
            class="px-4 py-2 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shrink-0"
        >
          Anmelden
        </NuxtLink>
      </div>
    </div>

    <!-- User Menu Popup -->
    <Menu ref="menu" id="user_menu" :model="items" :popup="true" class="w-56">
      <template #item="{ item }">
        <a v-if="!item.separator" class="flex items-center gap-3 p-3 hover:bg-gray-100 dark:hover:bg-neutral-700 cursor-pointer" @click="item.command">
          <Icon v-if="item.iconName" :name="item.iconName" class="text-gray-600 dark:text-gray-400" />
          <span class="text-gray-900 dark:text-white">{{ item.label }}</span>
        </a>
      </template>
      <template #start>
        <div class="p-3 border-b border-gray-200 dark:border-neutral-700">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center">
              <span class="text-purple-600 dark:text-purple-400 font-semibold">
                {{ user?.first_name?.charAt(0) }}{{ user?.last_name?.charAt(0) }}
              </span>
            </div>
            <div class="flex-1 min-w-0">
              <p class="font-semibold text-gray-900 dark:text-white truncate">
                {{ user?.first_name }} {{ user?.last_name }}
              </p>
              <p class="text-sm text-gray-500 dark:text-gray-400 truncate">
                {{ user?.email }}
              </p>
            </div>
          </div>
        </div>
      </template>
    </Menu>
  </header>

  <!-- Spacer to prevent content from going under fixed header -->
  <div class="h-20"></div>
</template>

<style scoped>
/* Gradient text for logo */
.bg-clip-text {
  -webkit-background-clip: text;
  background-clip: text;
}
</style>