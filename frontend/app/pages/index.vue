<script setup lang="ts">
import {useAuth} from "~/composables/useAuth";

definePageMeta({
  middleware: 'auth'
})
const {logout, user} = useAuth()
</script>

<template>
  <div class="p-6">
    <h1>Geschützte Seite</h1>
    <p>Du bist eingeloggt 🎉</p>
    <button
        class="mt-4 px-4 py-2 bg-red-500 text-white rounded"
        @click="logout"
    >
      Logout
    </button>
    <p>{{user}}</p>
  </div>
</template>