<script setup lang="ts">
import {useAuthStore} from '~/stores/auth';
import {useFriendshipsStore} from '~/stores/friendships';
import {useToast} from 'primevue/usetoast';

const {fetchUser} = useAuthStore();
await fetchUser();

const friendshipsStore = useFriendshipsStore();
const toast = useToast();

const activeTab = ref<'friends' | 'requests'>('friends');
const showAddDialog = ref(false);

onMounted(async () => {
  await friendshipsStore.fetchAll();
});

const handleRemoveFriend = async (friendId: number) => {
  try {
    await friendshipsStore.endFriendship(friendId);
    toast.add({
      severity: 'success',
      summary: 'Freundschaft beendet',
      detail: 'Die Freundschaft wurde beendet',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Freundschaft konnte nicht beendet werden',
      life: 3000
    });
  }
};

const handleAcceptRequest = async (requestId: number) => {
  try {
    await friendshipsStore.acceptRequest(requestId);
    toast.add({
      severity: 'success',
      summary: 'Anfrage angenommen',
      detail: 'Ihr seid jetzt Freunde!',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Anfrage konnte nicht angenommen werden',
      life: 3000
    });
  }
};

const handleDeclineRequest = async (requestId: number) => {
  try {
    await friendshipsStore.declineRequest(requestId);
    toast.add({
      severity: 'info',
      summary: 'Anfrage abgelehnt',
      detail: 'Die Anfrage wurde abgelehnt',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Anfrage konnte nicht abgelehnt werden',
      life: 3000
    });
  }
};

const handleCancelRequest = async (requestId: number) => {
  try {
    await friendshipsStore.cancelRequest(requestId);
    toast.add({
      severity: 'info',
      summary: 'Anfrage zurückgezogen',
      detail: 'Die Anfrage wurde zurückgezogen',
      life: 3000
    });
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Anfrage konnte nicht zurückgezogen werden',
      life: 3000
    });
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-4xl mx-auto">
        <!-- Page Header -->
        <div class="flex items-center justify-between mb-6">
          <div>
            <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Freunde</h1>
            <p class="text-gray-500 dark:text-gray-400 mt-1">Verwalte deine Freundschaften</p>
          </div>
          <button
              @click="showAddDialog = true"
              class="hidden sm:flex px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg items-center gap-2"
          >
            <Icon name="lucide:plus" />
            <span>Hinzufügen</span>
          </button>
        </div>

        <!-- Tabs -->
        <div class="flex gap-2 mb-6">
          <button
              @click="activeTab = 'friends'"
              class="flex-1 sm:flex-none px-6 py-3 rounded-lg font-medium transition-all"
              :class="activeTab === 'friends'
                ? 'bg-linear-to-r from-purple-600 to-pink-500 text-white shadow-lg'
                : 'bg-white dark:bg-neutral-800 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-neutral-700 hover:bg-gray-50 dark:hover:bg-neutral-700'"
          >
            <Icon name="lucide:users" class=" mr-2" />
            Freunde
            <span class="ml-2 px-2 py-0.5 rounded-full text-xs" :class="activeTab === 'friends' ? 'bg-white/20' : 'bg-gray-100 dark:bg-neutral-700'">
              {{ friendshipsStore.friendsCount }}
            </span>
          </button>
          <button
              @click="activeTab = 'requests'"
              class="flex-1 sm:flex-none px-6 py-3 rounded-lg font-medium transition-all"
              :class="activeTab === 'requests'
                ? 'bg-linear-to-r from-purple-600 to-pink-500 text-white shadow-lg'
                : 'bg-white dark:bg-neutral-800 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-neutral-700 hover:bg-gray-50 dark:hover:bg-neutral-700'"
          >
            <Icon name="lucide:inbox" class=" mr-2" />
            Anfragen
            <span
                v-if="friendshipsStore.totalRequestsCount > 0"
                class="ml-2 px-2 py-0.5 rounded-full text-xs"
                :class="activeTab === 'requests' ? 'bg-white/20' : 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400'"
            >
              {{ friendshipsStore.totalRequestsCount }}
            </span>
          </button>
        </div>

        <!-- Loading State -->
        <div v-if="friendshipsStore.loading" class="text-center py-16">
          <Icon name="lucide:loader-2" class="animate-spin text-4xl text-purple-600 dark:text-purple-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400">Wird geladen...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="friendshipsStore.error" class="p-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl">
          <div class="flex items-center gap-3">
            <Icon name="lucide:triangle-alert" class=" text-red-600 dark:text-red-400 text-xl" />
            <div>
              <p class="font-semibold text-red-900 dark:text-red-200">Fehler beim Laden</p>
              <p class="text-sm text-red-700 dark:text-red-300">{{ friendshipsStore.error }}</p>
            </div>
          </div>
        </div>

        <!-- Friends Tab -->
        <div v-else-if="activeTab === 'friends'" class="space-y-4">
          <FriendCard
              v-for="friend in friendshipsStore.friends"
              :key="friend.user_id"
              :friend="friend"
              @remove="handleRemoveFriend"
          />

          <!-- Empty State -->
          <div v-if="friendshipsStore.friends.length === 0" class="text-center py-16 px-6">
            <div class="w-20 h-20 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-full flex items-center justify-center mx-auto mb-4">
              <Icon name="lucide:users" class=" text-3xl text-purple-600 dark:text-purple-400" />
            </div>
            <h3 class="text-xl font-bold text-gray-900 dark:text-white mb-2">Noch keine Freunde</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">Füge Freunde hinzu, um Termine gemeinsam zu planen.</p>
            <button
                @click="showAddDialog = true"
                class="px-6 py-3 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-lg"
            >
              <Icon name="lucide:plus" class=" mr-2" />
              Freund hinzufügen
            </button>
          </div>
        </div>

        <!-- Requests Tab -->
        <div v-else-if="activeTab === 'requests'" class="space-y-6">
          <!-- Incoming Requests -->
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
              <Icon name="lucide:arrow-down" class=" text-green-500" />
              Eingehende Anfragen
              <span class="text-sm font-normal text-gray-500 dark:text-gray-400">
                ({{ friendshipsStore.incomingRequestsCount }})
              </span>
            </h2>

            <div class="space-y-3">
              <FriendRequestCard
                  v-for="request in friendshipsStore.incomingRequests"
                  :key="request.requestId"
                  :request="request"
                  type="incoming"
                  @accept="handleAcceptRequest"
                  @decline="handleDeclineRequest"
              />

              <div v-if="friendshipsStore.incomingRequests.length === 0" class="p-6 bg-gray-50 dark:bg-neutral-800 rounded-xl text-center">
                <p class="text-gray-500 dark:text-gray-400">Keine eingehenden Anfragen</p>
              </div>
            </div>
          </div>

          <!-- Outgoing Requests -->
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
              <Icon name="lucide:arrow-up" class=" text-blue-500" />
              Ausgehende Anfragen
              <span class="text-sm font-normal text-gray-500 dark:text-gray-400">
                ({{ friendshipsStore.outgoingRequestsCount }})
              </span>
            </h2>

            <div class="space-y-3">
              <FriendRequestCard
                  v-for="request in friendshipsStore.outgoingRequests"
                  :key="request.requestId"
                  :request="request"
                  type="outgoing"
                  @cancel="handleCancelRequest"
              />

              <div v-if="friendshipsStore.outgoingRequests.length === 0" class="p-6 bg-gray-50 dark:bg-neutral-800 rounded-xl text-center">
                <p class="text-gray-500 dark:text-gray-400">Keine ausgehenden Anfragen</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- FAB - Add Friend (Mobile) -->
    <button
        @click="showAddDialog = true"
        class="fixed bottom-6 right-6 w-14 h-14 sm:hidden rounded-full flex items-center justify-center text-white shadow-2xl bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all transform hover:scale-110 z-50"
    >
      <Icon name="lucide:plus" class=" text-xl" />
    </button>

    <!-- Send Request Dialog -->
    <SendFriendRequestDialog
        :visible="showAddDialog"
        @close="showAddDialog = false"
    />

    <Toast />
  </div>
</template>
