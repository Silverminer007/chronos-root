<script setup lang="ts">
import {useAuthStore} from "~/stores/auth";

definePageMeta({
  middleware: 'auth'
})
const {fetchUser} = useAuthStore()
await fetchUser()
const eventStore = useEventsStore()
callOnce('events', () => eventStore.fetchEvents())
</script>

<template>
  <!-- Header -->
  <SearchHeader/>
  <!-- Body -->
  <div class="p-4 pt-20 flex flex-col gap-4">
    <DateCard v-for="event in eventStore.events" :key="event.id" :event="event" />
    <p v-if="eventStore.error" class="text-red-500 text-sm"><span class="pi pi-exclamation-triangle"/> {{eventStore.error}}</p>
    <DateCardSkeleton v-if="eventStore.loading" v-for="i in [1, 2, 3, 4, 5]" :key="i"/>
    <div class="flex flex-row w-full items-center justify-center">
      <Button @click="eventStore.fetchEvents">
        Mehr laden
      </Button>
    </div>
    <div class="fab">
      <Button
          raised
          class="fixed bottom-6 right-6 rounded-full w-14 h-14 flex items-center justify-center text-2xl shadow-lg"
      >
        <span class="pi pi-plus"></span>
      </Button>
    </div>

  </div>
</template>

<style scoped>
.fab {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 999; /* wichtig, damit er über dem Content bleibt */
}
</style>