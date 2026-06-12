<script setup lang="ts">
import type {Group} from '~/types';
import ConfirmDialog from "~/components/ConfirmDialog.vue";

const props = defineProps<{
  group: Group;
}>();

const emit = defineEmits<{
  delete: [groupId: number];
}>();

const showConfirmDialog = ref(false);

const memberCount = computed(() => props.group.members?.length || 0);

const displayedMembers = computed(() => {
  if (!props.group.members) return [];
  return props.group.members.slice(0, 5);
});

const remainingCount = computed(() => {
  if (!props.group.members) return 0;
  return Math.max(0, props.group.members.length - 5);
});

const handleDelete = (e: Event) => {
  e.preventDefault();
  e.stopPropagation();
  showConfirmDialog.value = true;
};

const confirmDelete = () => {
  emit('delete', props.group.id);
  showConfirmDialog.value = false;
};
</script>

<template>
  <NuxtLink
      :to="`/groups/${group.id}`"
      class="block bg-white dark:bg-neutral-800 rounded-xl shadow-sm border border-gray-200 dark:border-neutral-700 p-4 hover:shadow-md hover:border-blue-300 dark:hover:border-blue-700 transition-all cursor-pointer"
  >
    <div class="flex items-start gap-4">
      <div
          class="w-12 h-12 bg-linear-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30 rounded-xl flex items-center justify-center shrink-0">
        <Icon name="lucide:users" class=" text-blue-600 dark:text-blue-400 text-xl" />
      </div>

      <div class="flex-1 min-w-0">
        <div class="flex items-center justify-between gap-2">
          <h3 class="font-semibold text-gray-900 dark:text-white truncate">{{ group.name }}</h3>
          <button
              class="p-2 text-gray-400 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors shrink-0"
              title="Gruppe löschen"
              @click="handleDelete"
          >
            <Icon name="lucide:trash-2" class=" text-sm" />
          </button>
        </div>

        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          {{ memberCount }} {{ memberCount === 1 ? 'Mitglied' : 'Mitglieder' }}
        </p>

        <!-- Member Avatars -->
        <div v-if="memberCount > 0" class="flex items-center mt-3">
          <div class="flex -space-x-2">
            <div
                v-for="member in displayedMembers"
                :key="member.id"
                class="w-8 h-8 rounded-full bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 flex items-center justify-center border-2 border-white dark:border-neutral-800"
                :title="`${member.first_name} ${member.last_name}`"
            >
              <span class="text-purple-600 dark:text-purple-400 font-semibold text-xs">
                {{ member.first_name?.charAt(0) }}{{ member.last_name?.charAt(0) }}
              </span>
            </div>
            <div
                v-if="remainingCount > 0"
                class="w-8 h-8 rounded-full bg-gray-200 dark:bg-neutral-700 flex items-center justify-center border-2 border-white dark:border-neutral-800"
            >
              <span class="text-gray-600 dark:text-gray-400 font-semibold text-xs">
                +{{ remainingCount }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <Icon name="lucide:chevron-right" class=" text-gray-400 dark:text-gray-500 shrink-0" />
    </div>
  </NuxtLink>

  <ConfirmDialog
      :visible="showConfirmDialog"
      title="Gruppe löschen"
      :message="`Möchtest du die Gruppe '${group.name}' wirklich löschen? Alle Mitglieder werden entfernt.`"
      confirm-text="Löschen"
      confirm-color="red"
      @close="showConfirmDialog = false"
      @confirm="confirmDelete"
  />
</template>
