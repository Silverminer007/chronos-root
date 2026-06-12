<script setup lang="ts">
import { useAuthStore } from '~/stores/auth';
import { useToast } from 'primevue/usetoast';
import type { LinkedAccount, Passkey } from '~/types';

const authStore = useAuthStore();
const toast = useToast();

await authStore.fetchUser();

// Profile form
const firstName = ref(authStore.user?.first_name ?? '');
const lastName = ref(authStore.user?.last_name ?? '');
const email = ref(authStore.user?.email ?? '');
const savingProfile = ref(false);

// Linked accounts
const PROVIDERS = [
  { id: 'google',    name: 'Google',    icon: 'simple-icons:google',    color: 'text-red-500' },
  //{ id: 'microsoft', name: 'Microsoft', icon: 'simple-icons:microsoft', color: 'text-blue-500' },
  //{ id: 'apple',     name: 'Apple',     icon: 'simple-icons:apple',     color: 'text-gray-800 dark:text-gray-100' },
] as const;

const linkedAccounts = ref<LinkedAccount[]>([]);
const loadingLinked = ref(false);
const unlinkingProvider = ref<string | null>(null);

const isLinked = (providerId: string) => linkedAccounts.value.some(a => a.provider === providerId);

const fetchLinkedAccounts = async () => {
  loadingLinked.value = true;
  try {
    linkedAccounts.value = await authStore.fetchLinkedAccounts();
  } catch {
    // silently ignore — section stays empty
  } finally {
    loadingLinked.value = false;
  }
};

const handleUnlink = async (providerId: string) => {
  unlinkingProvider.value = providerId;
  try {
    await authStore.unlinkAccount(providerId);
    linkedAccounts.value = linkedAccounts.value.filter(a => a.provider !== providerId);
    toast.add({ severity: 'success', summary: 'Konto getrennt', life: 3000 });
  } catch {
    toast.add({ severity: 'error', summary: 'Fehler', detail: 'Konto konnte nicht getrennt werden', life: 3000 });
  } finally {
    unlinkingProvider.value = null;
  }
};

// Passkeys
const passkeys = ref<Passkey[]>([]);
const loadingPasskeys = ref(false);
const showPasskeyDialog = ref(false);

const fetchPasskeys = async () => {
  loadingPasskeys.value = true;
  try {
    passkeys.value = await authStore.fetchPasskeys();
  } catch {
    // silently ignore
  } finally {
    loadingPasskeys.value = false;
  }
};

const formatDate = (iso: string) => {
  return new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
};

onMounted(() => {
  fetchLinkedAccounts();
  fetchPasskeys();
});

const handleSaveProfile = async () => {
  savingProfile.value = true;
  try {
    await authStore.updateProfile({
      first_name: firstName.value,
      last_name: lastName.value,
      email: email.value,
    });
    toast.add({ severity: 'success', summary: 'Profil gespeichert', life: 3000 });
  } catch {
    toast.add({ severity: 'error', summary: 'Fehler beim Speichern', detail: 'Bitte versuche es erneut', life: 3000 });
  } finally {
    savingProfile.value = false;
  }
};
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-neutral-900">
    <SearchHeader />

    <div class="container mx-auto px-4 sm:px-6 pb-24">
      <div class="max-w-2xl mx-auto">
        <!-- Page Header -->
        <div class="mb-6">
          <h1 class="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Profil</h1>
          <p class="text-gray-500 dark:text-gray-400 mt-1">Verwalte deine persönlichen Daten</p>
        </div>

        <!-- Profile Card -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6 mb-6">
          <!-- Avatar -->
          <div class="flex items-center gap-4 mb-6 pb-6 border-b border-gray-100 dark:border-neutral-700">
            <div class="w-16 h-16 rounded-full bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center shrink-0">
              <span class="text-purple-600 dark:text-purple-400 font-bold text-2xl">
                {{ firstName?.charAt(0) }}{{ lastName?.charAt(0) }}
              </span>
            </div>
            <div>
              <p class="font-semibold text-gray-900 dark:text-white text-lg">
                {{ firstName }} {{ lastName }}
              </p>
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ email }}</p>
            </div>
          </div>

          <!-- Form -->
          <div class="space-y-4">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                  Vorname
                </label>
                <input
                  v-model="firstName"
                  type="text"
                  class="w-full px-3 py-2.5 rounded-lg border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
                  placeholder="Vorname"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                  Nachname
                </label>
                <input
                  v-model="lastName"
                  type="text"
                  class="w-full px-3 py-2.5 rounded-lg border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
                  placeholder="Nachname"
                />
              </div>
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1.5">
                E-Mail-Adresse
              </label>
              <input
                v-model="email"
                type="email"
                class="w-full px-3 py-2.5 rounded-lg border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:border-purple-500 dark:focus:border-purple-400 focus:ring-2 focus:ring-purple-200 dark:focus:ring-purple-900/50 outline-none transition-all"
                placeholder="deine@email.de"
              />
            </div>

            <div class="flex justify-between pt-2">
              <button
                  class="flex items-center gap-2 px-5 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                  @click="authStore.changePassword()"
              >
                <Icon name="lucide:key" />
                <span>Passwort ändern</span>
              </button>
              <button
                :disabled="savingProfile"
                class="flex items-center gap-2 px-5 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
                @click="handleSaveProfile"
              >
                <Icon :name="savingProfile ? 'lucide:loader-2' : 'lucide:save'" :class="{ 'animate-spin': savingProfile }" />
                <span>Speichern</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Linked Accounts Card -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6 mb-6">
          <div class="flex items-center gap-3 mb-5">
            <div class="w-9 h-9 rounded-lg bg-purple-50 dark:bg-purple-900/20 flex items-center justify-center">
              <Icon name="lucide:link" class="text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="font-semibold text-gray-900 dark:text-white">Verknüpfte Konten</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Melde dich mit einem externen Anbieter an</p>
            </div>
          </div>

          <!-- Loading -->
          <div v-if="loadingLinked" class="flex items-center justify-center py-6">
            <Icon name="lucide:loader-2" class="animate-spin text-purple-500 text-xl" />
          </div>

          <!-- Provider list -->
          <div v-else class="divide-y divide-gray-100 dark:divide-neutral-700">
            <div
              v-for="provider in PROVIDERS"
              :key="provider.id"
              class="flex items-center justify-between py-3.5 first:pt-0 last:pb-0"
            >
              <div class="flex items-center gap-3">
                <Icon :name="provider.icon" class="text-xl shrink-0" :class="provider.color" />
                <div>
                  <p class="font-medium text-gray-900 dark:text-white">{{ provider.name }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ isLinked(provider.id) ? 'Verknüpft' : 'Nicht verknüpft' }}
                  </p>
                </div>
              </div>

              <!-- Linked: unlink button -->
              <button
                v-if="isLinked(provider.id)"
                :disabled="unlinkingProvider === provider.id"
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                @click="handleUnlink(provider.id)"
              >
                <Icon
                  :name="unlinkingProvider === provider.id ? 'lucide:loader-2' : 'lucide:unlink'"
                  class="text-sm"
                  :class="{ 'animate-spin': unlinkingProvider === provider.id }"
                />
                <span>Trennen</span>
              </button>

              <!-- Not linked: link button -->
              <button
                v-else
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-purple-600 dark:text-purple-400 border border-purple-200 dark:border-purple-700 hover:bg-purple-50 dark:hover:bg-purple-900/20 transition-colors"
                @click="authStore.linkAccount(provider.id)"
              >
                <Icon name="lucide:link" class="text-sm" />
                <span>Verknüpfen</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Passkeys Card -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-6 mb-6">
          <div class="flex items-center justify-between mb-5">
            <div class="flex items-center gap-3">
              <div class="w-9 h-9 rounded-lg bg-purple-50 dark:bg-purple-900/20 flex items-center justify-center">
                <Icon name="lucide:fingerprint" class="text-purple-600 dark:text-purple-400" />
              </div>
              <div>
                <p class="font-semibold text-gray-900 dark:text-white">Passkeys</p>
                <p class="text-sm text-gray-500 dark:text-gray-400">Passwortlos anmelden mit Fingerabdruck oder Gesicht</p>
              </div>
            </div>
            <button
              class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-purple-600 dark:text-purple-400 border border-purple-200 dark:border-purple-700 hover:bg-purple-50 dark:hover:bg-purple-900/20 transition-colors shrink-0"
              @click="authStore.createPasskey()"
            >
              <Icon name="lucide:plus" class="text-sm" />
              <span>Hinzufügen</span>
            </button>
          </div>

          <!-- Loading -->
          <div v-if="loadingPasskeys" class="flex items-center justify-center py-6">
            <Icon name="lucide:loader-2" class="animate-spin text-purple-500 text-xl" />
          </div>

          <!-- Empty state -->
          <div v-else-if="passkeys.length === 0" class="flex flex-col items-center justify-center py-8 text-center">
            <div class="w-12 h-12 rounded-full bg-gray-100 dark:bg-neutral-700 flex items-center justify-center mb-3">
              <Icon name="lucide:fingerprint" class="text-gray-400 dark:text-gray-500 text-xl" />
            </div>
            <p class="text-sm font-medium text-gray-600 dark:text-gray-400">Keine Passkeys hinterlegt</p>
            <p class="text-xs text-gray-400 dark:text-gray-500 mt-1">Füge einen Passkey hinzu, um dich ohne Passwort anzumelden</p>
          </div>

          <!-- Passkey list -->
          <div v-else class="divide-y divide-gray-100 dark:divide-neutral-700">
            <div
              v-for="passkey in passkeys"
              :key="passkey.id"
              class="flex items-center gap-3 py-3.5 first:pt-0 last:pb-0"
            >
              <div class="w-9 h-9 rounded-lg bg-gray-100 dark:bg-neutral-700 flex items-center justify-center shrink-0">
                <Icon name="lucide:key-round" class="text-gray-500 dark:text-gray-400" />
              </div>
              <div class="flex-1 min-w-0">
                <p class="font-medium text-gray-900 dark:text-white truncate">{{ passkey.name }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">
                  Erstellt am {{ formatDate(passkey.created_at) }}
                  <span v-if="passkey.last_used_at"> · Zuletzt genutzt {{ formatDate(passkey.last_used_at) }}</span>
                </p>
              </div>
              <button
                  class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-red-600 dark:text-red-400 border border-red-200 dark:border-red-800 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  @click="authStore.deletePasskey(passkey.id)"
              >
                <Icon
                    name="lucide:x"
                    class="text-sm"
                />
                <span>Entfernen</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Passkey registration dialog -->
        <Teleport to="body">
          <div
            v-if="showPasskeyDialog"
            class="fixed inset-0 z-50 flex items-center justify-center p-4"
            @click.self="showPasskeyDialog = false"
          >
            <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" />
            <div class="relative bg-white dark:bg-neutral-800 rounded-2xl shadow-2xl border border-gray-200 dark:border-neutral-700 p-6 w-full max-w-sm">
              <div class="flex items-center gap-3 mb-4">
                <div class="w-10 h-10 rounded-xl bg-purple-50 dark:bg-purple-900/20 flex items-center justify-center">
                  <Icon name="lucide:fingerprint" class="text-purple-600 dark:text-purple-400 text-xl" />
                </div>
                <h2 class="text-lg font-semibold text-gray-900 dark:text-white">Passkey hinzufügen</h2>
              </div>
              <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
                Du wirst zu deinen Kontoeinstellungen weitergeleitet, um einen neuen Passkey zu registrieren. Kehre danach zu dieser Seite zurück.
              </p>
              <div class="flex gap-3">
                <button
                  class="flex-1 px-4 py-2.5 rounded-lg font-medium text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-neutral-600 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors"
                  @click="showPasskeyDialog = false"
                >
                  Abbrechen
                </button>
                <button
                  class="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg font-medium text-white bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 transition-all shadow-sm"
                  @click="authStore.createPasskey()"
                >
                  <Icon name="lucide:external-link" class="text-sm" />
                  <span>Weiter</span>
                </button>
              </div>
            </div>
          </div>
        </Teleport>
      </div>
    </div>

    <Toast />
  </div>
</template>
