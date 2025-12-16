<script setup lang="ts">
import Avatar from "primevue/avatar";
import {ref} from "vue";
import {useAuthStore} from "~/stores/auth";

const {search} = useEventsStore()
const {logout, user, authenticated} = useAuthStore()

const searchQuery = ref("");

const menu = ref();
const items = ref([
  {
    label: 'Options',
    items: [
      {
        label: 'Logout',
        icon: 'pi pi-sign-out',
        command: () => logout(),
      }
    ]
  }
]);

const toggle = (event) => {
  menu.value.toggle(event);
};
</script>

<template>
  <div class="flex flex-row items-center justify-center w-full gap-2 p-2 fixed bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 z-30">
    <!-- Search Events -->
    <FloatLabel variant="in">
      <InputText fluid id="searchQuery" type="text" v-model="searchQuery" @valueChange="search(searchQuery)"/>
      <label for="searchQuery">
        <span class="pi pi-search"></span> Search</label>
    </FloatLabel>
    <Avatar v-if="authenticated" :label="user?.first_name?.charAt(0)" class="mr-2" size="large" shape="circle"
            @click="toggle" aria-haspopup="true" aria-controls="overlay_menu"/>
    <Menu ref="menu" id="overlay_menu" :model="items" :popup="true"/>
  </div>
</template>

<style scoped>

</style>