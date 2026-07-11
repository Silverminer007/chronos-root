<script setup lang="ts">
import { useFriends } from '~/composables/useFriends'
import { useGroups } from '~/composables/useGroups'
import type { Friend, Group, Role } from '~/types'

type ParticipationRole = Exclude<Role, 'NONE'>

interface SelectedUser { id: number; name: string; role: ParticipationRole }
interface SelectedGroup { id: number; name: string; role: ParticipationRole; memberCount: number }

const emit = defineEmits<{ change: [userCount: number, groupMemberCount: number] }>()

const { searchFriends, loading: friendsLoading } = useFriends()
const { searchGroups, loading: groupsLoading } = useGroups()

const tab = ref<'users' | 'groups'>('users')
const query = ref('')
const userResults = ref<Friend[]>([])
const groupResults = ref<Group[]>([])
const selectedUsers = ref<SelectedUser[]>([])
const selectedGroups = ref<SelectedGroup[]>([])

const isLoading = computed(() => tab.value === 'users' ? friendsLoading.value : groupsLoading.value)
const hasResults = computed(() =>
  query.value.length >= 2 && (tab.value === 'users' ? userResults.value.length > 0 : groupResults.value.length > 0)
)
const noResults = computed(() =>
  query.value.length >= 2 && !isLoading.value &&
  (tab.value === 'users' ? userResults.value.length === 0 : groupResults.value.length === 0)
)

let debounceTimer: ReturnType<typeof setTimeout>

onUnmounted(() => clearTimeout(debounceTimer))

function onInput() {
  clearTimeout(debounceTimer)
  if (query.value.length < 2) {
    userResults.value = []
    groupResults.value = []
    return
  }
  debounceTimer = setTimeout(doSearch, 250)
}

async function doSearch() {
  if (tab.value === 'users') {
    userResults.value = await searchFriends(query.value)
  } else {
    const res = await searchGroups(query.value)
    groupResults.value = Array.isArray(res) ? res : []
  }
}

watch(tab, () => {
  query.value = ''
  userResults.value = []
  groupResults.value = []
})

function selectUser(friend: Friend) {
  if (selectedUsers.value.some(u => u.id === friend.user_id)) return
  selectedUsers.value.push({ id: friend.user_id, name: friend.name, role: 'ATTENDANT' })
  query.value = ''
  userResults.value = []
}

function selectGroup(group: Group) {
  if (selectedGroups.value.some(g => g.id === group.id)) return
  selectedGroups.value.push({ id: group.id, name: group.name, role: 'ATTENDANT', memberCount: group.members?.length ?? 0 })
  query.value = ''
  groupResults.value = []
}

function removeUser(id: number) {
  selectedUsers.value = selectedUsers.value.filter(u => u.id !== id)
}

function removeGroup(id: number) {
  selectedGroups.value = selectedGroups.value.filter(g => g.id !== id)
}

const ROLE_CYCLE: ParticipationRole[] = ['ATTENDANT', 'RESPONSIBLE', 'HELPER', 'GUEST']

const ROLE_META: Record<ParticipationRole, { short: string; class: string }> = {
  ATTENDANT:   { short: 'TN',   class: 'bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300' },
  RESPONSIBLE: { short: 'Org',  class: 'bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300' },
  HELPER:      { short: 'Helf', class: 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300' },
  GUEST:       { short: 'Gast', class: 'bg-gray-100 dark:bg-neutral-600 text-gray-600 dark:text-gray-300' },
}

function cycleRole(item: SelectedUser | SelectedGroup): void {
  const idx = ROLE_CYCLE.indexOf(item.role)
  item.role = ROLE_CYCLE[(idx + 1) % ROLE_CYCLE.length]!
}

watch([selectedUsers, selectedGroups], () => {
  const groupMemberCount = selectedGroups.value.reduce((s, g) => s + g.memberCount, 0)
  emit('change', selectedUsers.value.length, groupMemberCount)
}, { deep: true })

defineExpose({
  getSelectedUsers: () => selectedUsers.value,
  getSelectedGroups: () => selectedGroups.value,
})
</script>

<template>
  <div class="space-y-3">
    <!-- Tab switcher -->
    <div class="flex gap-1 p-1 bg-gray-100 dark:bg-neutral-700 rounded-xl">
      <button
        type="button"
        class="flex-1 flex items-center justify-center gap-1.5 py-1.5 px-3 rounded-lg text-sm font-medium transition-all"
        :class="tab === 'users'
          ? 'bg-white dark:bg-neutral-600 text-gray-900 dark:text-white shadow-sm'
          : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'"
        @click="tab = 'users'"
      >
        <Icon name="lucide:user" class="text-xs" />
        Personen
      </button>
      <button
        type="button"
        class="flex-1 flex items-center justify-center gap-1.5 py-1.5 px-3 rounded-lg text-sm font-medium transition-all"
        :class="tab === 'groups'
          ? 'bg-white dark:bg-neutral-600 text-gray-900 dark:text-white shadow-sm'
          : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'"
        @click="tab = 'groups'"
      >
        <Icon name="lucide:users" class="text-xs" />
        Gruppen
      </button>
    </div>

    <!-- Search input -->
    <div class="relative">
      <Icon name="lucide:search" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm pointer-events-none" />
      <input
        v-model="query"
        type="text"
        :placeholder="tab === 'users' ? 'Freund suchen…' : 'Gruppe suchen…'"
        class="w-full pl-9 pr-4 py-2.5 rounded-xl border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white text-sm focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all placeholder:text-gray-400 dark:placeholder:text-neutral-500"
        @input="onInput"
      />
      <Icon
        v-if="isLoading"
        name="lucide:loader-circle"
        class="absolute right-3 top-1/2 -translate-y-1/2 text-purple-500 animate-spin text-sm"
      />
    </div>

    <!-- Search results -->
    <div
      v-if="hasResults"
      class="rounded-xl border border-gray-100 dark:border-neutral-700 overflow-hidden max-h-44 overflow-y-auto"
    >
      <!-- User results -->
      <template v-if="tab === 'users'">
        <button
          v-for="friend in userResults"
          :key="friend.user_id"
          type="button"
          :disabled="selectedUsers.some(u => u.id === friend.user_id)"
          class="w-full flex items-center gap-3 px-3 py-2.5 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors text-left disabled:opacity-40 disabled:cursor-not-allowed"
          @click="selectUser(friend)"
        >
          <div class="w-8 h-8 rounded-full bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center flex-shrink-0">
            <Icon name="lucide:user" class="text-purple-600 dark:text-purple-400 text-xs" />
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-gray-900 dark:text-white truncate">{{ friend.name }}</p>
            <p v-if="friend.email" class="text-xs text-gray-500 dark:text-gray-400 truncate">{{ friend.email }}</p>
          </div>
          <Icon
            v-if="selectedUsers.some(u => u.id === friend.user_id)"
            name="lucide:check"
            class="text-purple-500 text-sm flex-shrink-0"
          />
        </button>
      </template>

      <!-- Group results -->
      <template v-else>
        <button
          v-for="group in groupResults"
          :key="group.id"
          type="button"
          :disabled="selectedGroups.some(g => g.id === group.id)"
          class="w-full flex items-center gap-3 px-3 py-2.5 hover:bg-gray-50 dark:hover:bg-neutral-700 transition-colors text-left disabled:opacity-40 disabled:cursor-not-allowed"
          @click="selectGroup(group)"
        >
          <div class="w-8 h-8 rounded-full bg-linear-to-br from-blue-100 to-cyan-100 dark:from-blue-900/30 dark:to-cyan-900/30 flex items-center justify-center flex-shrink-0">
            <Icon name="lucide:users" class="text-blue-600 dark:text-blue-400 text-xs" />
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-gray-900 dark:text-white truncate">{{ group.name }}</p>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ group.members?.length ?? 0 }} Mitglieder</p>
          </div>
          <Icon
            v-if="selectedGroups.some(g => g.id === group.id)"
            name="lucide:check"
            class="text-blue-500 text-sm flex-shrink-0"
          />
        </button>
      </template>
    </div>

    <!-- No results -->
    <p v-else-if="noResults" class="text-center text-sm text-gray-400 dark:text-neutral-500 py-1">
      Keine Ergebnisse
    </p>

    <!-- Selected chips -->
    <div
      v-if="selectedUsers.length > 0 || selectedGroups.length > 0"
      class="flex flex-wrap gap-2 pt-1"
    >
      <!-- User chips -->
      <div
        v-for="user in selectedUsers"
        :key="'u-' + user.id"
        class="flex items-center gap-1.5 pl-2 pr-1 py-1 rounded-full bg-white dark:bg-neutral-700 border border-gray-200 dark:border-neutral-600 shadow-sm"
      >
        <Icon name="lucide:user" class="text-gray-400 dark:text-gray-500 text-xs flex-shrink-0" />
        <span class="text-xs font-medium text-gray-800 dark:text-gray-200 max-w-[90px] truncate">{{ user.name }}</span>
        <button
          type="button"
          class="text-[10px] px-1.5 py-0.5 rounded-full font-semibold transition-colors flex-shrink-0"
          :class="ROLE_META[user.role].class"
          title="Tippen zum Wechseln"
          @click="cycleRole(user)"
        >{{ ROLE_META[user.role].short }}</button>
        <button
          type="button"
          class="w-4 h-4 flex items-center justify-center rounded-full text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-200 dark:hover:bg-neutral-600 transition-colors flex-shrink-0"
          @click="removeUser(user.id)"
        >
          <Icon name="lucide:x" class="text-[10px]" />
        </button>
      </div>

      <!-- Group chips -->
      <div
        v-for="group in selectedGroups"
        :key="'g-' + group.id"
        class="flex items-center gap-1.5 pl-2 pr-1 py-1 rounded-full bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 shadow-sm"
      >
        <Icon name="lucide:users" class="text-blue-500 dark:text-blue-400 text-xs flex-shrink-0" />
        <span class="text-xs font-medium text-blue-800 dark:text-blue-200 max-w-[90px] truncate">{{ group.name }}</span>
        <button
          type="button"
          class="text-[10px] px-1.5 py-0.5 rounded-full font-semibold transition-colors flex-shrink-0"
          :class="ROLE_META[group.role].class"
          title="Tippen zum Wechseln"
          @click="cycleRole(group)"
        >{{ ROLE_META[group.role].short }}</button>
        <button
          type="button"
          class="w-4 h-4 flex items-center justify-center rounded-full text-blue-400 hover:text-blue-700 dark:hover:text-blue-200 hover:bg-blue-100 dark:hover:bg-blue-800/50 transition-colors flex-shrink-0"
          @click="removeGroup(group.id)"
        >
          <Icon name="lucide:x" class="text-[10px]" />
        </button>
      </div>
    </div>
  </div>
</template>