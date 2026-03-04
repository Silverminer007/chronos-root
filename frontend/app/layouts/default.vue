<template>
  <PwaInstallDialog/>
  <PushPermissionModal/>
  <NewFeaturesDialog/>
  <slot/>
</template>
<script setup lang="ts">
import PushPermissionModal from "~/components/PushPermissionModal.vue";
</script>