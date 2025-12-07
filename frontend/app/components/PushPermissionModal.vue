<template>
  <p v-if="visible">Test</p>
  <Dialog v-model:visible="visible" header="Push-Benachrichtigungen aktivieren?" modal @hide="dismiss">
    <p>Du bekommst so Terminerinnerungen, bekommst Neuigkeiten zu Terminen und wirst an Abstimmungen erinnert</p>
    <p>Wir benötigen von dir die Erlaubnis der Benachrichtigungen zu senden</p>

    <div class="flex gap-3 mt-4">
      <Button label="Aktivieren" @click="enable" />
      <Button label="Nicht jetzt" text @click="deny" />
    </div>
  </Dialog>
</template>

<script setup>
import Dialog from 'primevue/dialog';
import { usePush } from "~/composables/usePush.ts"

const { shouldAsk, markAsked, subscribe } = usePush()

const visible = ref(false)

onMounted(async () => {
  if (await shouldAsk()) {
    visible.value = true
  }
})

async function enable() {
  await subscribe()
  markAsked('granted')
  visible.value = false
}

function deny() {
  markAsked('denied')
  visible.value = false
}

function dismiss() {
  markAsked('dismissed')
  visible.value = false
}
</script>