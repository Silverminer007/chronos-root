<template>
  <div>
    <PwaInstallDialog/>
    <PushPermissionModal/>
    <NewFeaturesDialog/>
    <slot/>
  </div>
</template>
<script setup lang="ts">
import PushPermissionModal from "~/components/PushPermissionModal.vue";
</script>