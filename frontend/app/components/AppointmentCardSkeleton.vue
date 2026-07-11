<script setup lang="ts">
import Skeleton from 'primevue/skeleton';

</script>

<template>
  <Card>
    <template #title>
      <div class="flex flex-row items-center justify-between flex-wrap">
        <Skeleton height="2rem" class="mb-2" width="90%" border-radius="16px"></Skeleton>
        <div class="flex flex-row">
          <Skeleton v-for="i in [1,2,3,4]" :key="i" shape="circle" size="2rem" class="-m-1"></Skeleton>
        </div>
      </div>
    </template>
    <template #content>
      <div class="flex flex-col gap-2">
        <Skeleton height="1.5rem" width="85%" class="mb-2" border-radius="16px"></Skeleton>
        <Skeleton height="4rem" width="80%" class="mb-2" border-radius="16px"></Skeleton>
        <div class="flex flex-row items-center justify-between gap-2 not-sm:hidden">
          <Skeleton height="3rem" width="8rem" class="mb-2" border-radius="8px"></Skeleton>
          <Skeleton height="3rem" width="8rem" class="mb-2" border-radius="8px"></Skeleton>
          <Skeleton height="3rem" width="8rem" class="mb-2" border-radius="8px"></Skeleton>
        </div>
        <div class="flex flex-row items-center justify-between gap-2 sm:hidden">
          <Skeleton height="2.5rem" width="3rem" class="mb-2" border-radius="8px"></Skeleton>
          <Skeleton height="2.5rem" width="3rem" class="mb-2" border-radius="8px"></Skeleton>
          <Skeleton height="2.5rem" width="3rem" class="mb-2" border-radius="8px"></Skeleton>
        </div>
      </div>
    </template>
  </Card>
</template>

<style scoped>

</style>