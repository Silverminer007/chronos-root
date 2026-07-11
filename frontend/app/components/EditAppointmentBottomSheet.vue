<script setup lang="ts">
import {useAppointmentsStore} from '~/stores/appointments'
import {useToast} from 'primevue/usetoast'
import type {Appointment} from '~/types'

const props = defineProps<{ modelValue: boolean; appointment: Appointment }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; 'saved': [] }>()

const appointmentStore = useAppointmentsStore()
const toast = useToast()

const titleInput = ref<HTMLInputElement | null>(null)
const scrollEl = ref<HTMLElement | null>(null)
const saving = ref(false)
const showCustomDuration = ref(false)
const selectedDuration = ref<number | null>(60)
const customDurationValue = ref(30)
const customDurationUnit = ref<'minutes' | 'hours' | 'days'>('minutes')
const startStr = ref('')

const form = ref({
  name: '',
  description: '',
  venue: '',
  hasAttendees: false,
  minimalAttendees: 5
})

function initDefaults() {
  const appt = props.appointment
  const startDate = new Date(appt.start)
  const endDate = new Date(appt.end)
  const durationMinutes = Math.round((endDate.getTime() - startDate.getTime()) / 60000)

  startStr.value = toDatetimeLocal(startDate)
  form.value = {
    name: appt.name,
    description: appt.description || '',
    venue: appt.venue || '',
    hasAttendees: appt.minimal_attendees > 0,
    minimalAttendees: appt.minimal_attendees > 0 ? appt.minimal_attendees : 5,
  }

  const matchingChip = DURATION_CHIPS.find(c => c.minutes === durationMinutes)
  if (matchingChip) {
    selectedDuration.value = durationMinutes
    showCustomDuration.value = false
  } else {
    selectedDuration.value = null
    showCustomDuration.value = true
    if (durationMinutes % 1440 === 0) {
      customDurationUnit.value = 'days'
      customDurationValue.value = durationMinutes / 1440
    } else if (durationMinutes % 60 === 0) {
      customDurationUnit.value = 'hours'
      customDurationValue.value = durationMinutes / 60
    } else {
      customDurationUnit.value = 'minutes'
      customDurationValue.value = durationMinutes
    }
  }
}

watch(() => props.modelValue, (val) => {
  if (val) {
    initDefaults()
    nextTick(() => {
      if (scrollEl.value) scrollEl.value.scrollTop = 0
      titleInput.value?.focus()
    })
  }
})

function selectDuration(minutes: number) {
  selectedDuration.value = minutes
  showCustomDuration.value = false
}

function toggleCustomDuration() {
  showCustomDuration.value = !showCustomDuration.value
  if (showCustomDuration.value) selectedDuration.value = null
  else selectedDuration.value = 60
}

const effectiveDuration = computed(() => {
  if (showCustomDuration.value) {
    if (customDurationValue.value <= 0) return null
    const unit = DURATION_UNITS.find(u => u.value === customDurationUnit.value)!
    return customDurationValue.value * unit.multiplier
  }
  return selectedDuration.value
})

const startDate = computed(() => startStr.value ? new Date(startStr.value) : null)

const computedEnd = computed(() => {
  if (!startDate.value || !effectiveDuration.value) return null
  return new Date(startDate.value.getTime() + effectiveDuration.value * 60000)
})

const isValid = computed(() =>
    form.value.name.trim().length > 0 && startDate.value !== null && computedEnd.value !== null
)

const hasChanges = computed(() => {
  if (!startDate.value || !computedEnd.value) return false
  const appt = props.appointment
  return (
      form.value.name !== appt.name ||
      form.value.description !== (appt.description || '') ||
      form.value.venue !== (appt.venue || '') ||
      startDate.value.toISOString() !== appt.start ||
      computedEnd.value.toISOString() !== appt.end ||
      (form.value.hasAttendees ? form.value.minimalAttendees : 0) !== appt.minimal_attendees
  )
})

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  if (!isValid.value || !hasChanges.value || saving.value) return
  saving.value = true
  try {
    await appointmentStore.updateAppointment(props.appointment.id, {
      name: form.value.name.trim(),
      description: form.value.description.trim() || null,
      start: startDate.value!.toISOString(),
      end: computedEnd.value!.toISOString(),
      venue: form.value.venue.trim() || null,
      minimal_attendees: form.value.hasAttendees ? form.value.minimalAttendees : 0,
    })
    await appointmentStore.fetchAppointment(props.appointment.id)
    toast.add({severity: 'success', summary: 'Änderungen gespeichert', life: 3000})
    close()
    emit('saved')
  } catch {
    toast.add({severity: 'error', summary: 'Fehler', detail: 'Änderungen konnten nicht gespeichert werden', life: 3000})
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <!-- Backdrop -->
    <Transition name="bs-fade">
      <div
          v-if="modelValue"
          class="fixed inset-0 bg-black/50 z-40 backdrop-blur-sm"
          @click="close"
      />
    </Transition>

    <!-- Sheet -->
    <Transition name="bs-slide">
      <div
          v-if="modelValue"
          class="fixed bottom-0 left-0 right-0 z-50 bg-white dark:bg-neutral-800 rounded-t-2xl shadow-2xl flex flex-col"
          style="max-height: 90dvh"
      >
        <!-- Handle bar -->
        <div class="flex justify-center pt-3 pb-1 flex-shrink-0">
          <div class="w-10 h-1 bg-gray-300 dark:bg-neutral-600 rounded-full"/>
        </div>

        <!-- Header -->
        <div
            class="flex items-center justify-between px-6 py-3 border-b border-gray-100 dark:border-neutral-700 flex-shrink-0">
          <h2 class="text-lg font-bold text-gray-900 dark:text-white">Termin bearbeiten</h2>
          <button
              class="w-8 h-8 rounded-full flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-neutral-700 transition-colors"
              @click="close"
          >
            <Icon name="lucide:x" class="text-base"/>
          </button>
        </div>

        <!-- Scrollable content -->
        <div ref="scrollEl" class="flex-1 overflow-y-auto px-6 py-5 space-y-4">
          <!-- Title -->
          <input
              ref="titleInput"
              v-model="form.name"
              type="text"
              placeholder="Terminname…"
              class="w-full text-xl font-bold px-4 py-3 rounded-xl border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white placeholder:text-gray-400 dark:placeholder:text-neutral-400 focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all"
          />

          <!-- Description -->
          <textarea
              v-model="form.description"
              rows="3"
              placeholder="Beschreibung…"
              class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-purple-500 resize-none transition-all"
          />

          <!-- Venue -->
          <div class="relative">
            <Icon name="lucide:map-pin" class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-base"/>
            <input
                v-model="form.venue"
                type="text"
                placeholder="Ort…"
                class="w-full pl-10 pr-4 py-3 rounded-xl border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all"
            />
          </div>

          <!-- Start datetime -->
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">Start</label>
            <input
                v-model="startStr"
                type="datetime-local"
                class="w-full px-4 py-3 rounded-xl border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all"
            />
          </div>

          <!-- Duration chips -->
          <div>
            <label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-2">Dauer</label>
            <div class="flex flex-wrap gap-2">
              <button
                  v-for="chip in DURATION_CHIPS"
                  :key="chip.minutes"
                  type="button"
                  class="px-3 py-2 rounded-full text-sm font-medium transition-all"
                  :class="!showCustomDuration && selectedDuration === chip.minutes
                ? 'bg-purple-600 text-white shadow-sm'
                : 'bg-gray-100 dark:bg-neutral-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-neutral-600'"
                  @click="selectDuration(chip.minutes)"
              >
                {{ chip.label }}
              </button>
              <button
                  type="button"
                  class="px-3 py-2 rounded-full text-sm font-medium transition-all"
                  :class="showCustomDuration
                ? 'bg-purple-600 text-white shadow-sm'
                : 'bg-gray-100 dark:bg-neutral-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-neutral-600'"
                  @click="toggleCustomDuration"
              >
                Eigene…
              </button>
            </div>
            <div v-if="showCustomDuration" class="mt-3 space-y-2">
              <div class="flex gap-2">
                <button
                    v-for="unit in DURATION_UNITS"
                    :key="unit.value"
                    type="button"
                    class="flex-1 py-2 rounded-xl text-sm font-semibold transition-all"
                    :class="customDurationUnit === unit.value
                  ? 'bg-purple-600 text-white shadow-sm'
                  : 'bg-gray-100 dark:bg-neutral-700 text-gray-600 dark:text-gray-400 hover:bg-purple-50 dark:hover:bg-purple-900/20'"
                    @click="customDurationUnit = unit.value"
                >
                  {{ unit.label }}
                </button>
              </div>
              <input
                  v-model.number="customDurationValue"
                  type="number"
                  min="1"
                  class="w-full px-3 py-2 rounded-xl border border-gray-200 dark:border-neutral-600 bg-gray-50 dark:bg-neutral-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-purple-500"
              />
            </div>
          </div>

          <!-- Attendees toggle -->
          <div class="flex items-center justify-between">
            <div>
              <p class="font-medium text-gray-900 dark:text-white">Teilnehmer</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Mindestteilnehmer festlegen</p>
            </div>
            <button
                type="button"
                class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors flex-shrink-0"
                :class="form.hasAttendees ? 'bg-purple-600' : 'bg-gray-200 dark:bg-neutral-600'"
                @click="form.hasAttendees = !form.hasAttendees"
            >
            <span
                class="inline-block h-4 w-4 transform rounded-full bg-white shadow-sm transition-transform"
                :class="form.hasAttendees ? 'translate-x-6' : 'translate-x-1'"
            />
            </button>
          </div>

          <!-- Min attendees stepper -->
          <Transition name="expand">
            <div
                v-if="form.hasAttendees"
                class="flex items-center gap-4 px-4 py-3 bg-purple-50 dark:bg-purple-900/20 rounded-xl border border-purple-100 dark:border-purple-800"
            >
              <span class="text-sm font-medium text-purple-900 dark:text-purple-200">Mindestteilnehmer</span>
              <div class="flex items-center gap-3 ml-auto">
                <button
                    type="button"
                    class="w-8 h-8 rounded-full bg-purple-100 dark:bg-purple-800 text-purple-700 dark:text-purple-200 flex items-center justify-center font-bold text-lg leading-none hover:bg-purple-200 dark:hover:bg-purple-700 transition-colors"
                    @click="form.minimalAttendees = Math.max(0, form.minimalAttendees - 1)"
                >−
                </button>
                <span class="w-8 text-center font-bold text-purple-900 dark:text-purple-100 text-lg">{{
                    form.minimalAttendees
                  }}</span>
                <button
                    type="button"
                    class="w-8 h-8 rounded-full bg-purple-100 dark:bg-purple-800 text-purple-700 dark:text-purple-200 flex items-center justify-center font-bold text-lg leading-none hover:bg-purple-200 dark:hover:bg-purple-700 transition-colors"
                    @click="form.minimalAttendees++"
                >+
                </button>
              </div>
            </div>
          </Transition>

          <!-- Changes info -->
          <div
v-if="hasChanges"
               class="p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl">
            <div class="flex gap-3">
              <Icon name="lucide:info" class="text-yellow-600 dark:text-yellow-400 mt-0.5 shrink-0"/>
              <p class="text-sm text-yellow-800 dark:text-yellow-200">Alle Teilnehmer werden über die Änderungen
                benachrichtigt.</p>
            </div>
          </div>
        </div>

        <!-- Submit button -->
        <div
class="px-6 pt-4 border-t border-gray-100 dark:border-neutral-700 flex-shrink-0"
             style="padding-bottom: max(1rem, env(safe-area-inset-bottom))">
          <button
              type="button"
              :disabled="!isValid || !hasChanges || saving"
              class="w-full py-4 rounded-2xl font-bold text-lg text-white transition-all"
              :class="isValid && hasChanges && !saving
            ? 'bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 shadow-lg active:scale-[0.98]'
            : 'bg-gray-200 dark:bg-neutral-700 text-gray-400 dark:text-neutral-500 cursor-not-allowed'"
              @click="submit"
          >
          <span v-if="saving" class="flex items-center justify-center gap-2">
            <Icon name="lucide:loader-circle" class="animate-spin"/>
            Speichere…
          </span>
            <span v-else>Änderungen speichern</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.bs-fade-enter-active, .bs-fade-leave-active {
  transition: opacity 0.3s ease;
}

.bs-fade-enter-from, .bs-fade-leave-to {
  opacity: 0;
}

.bs-slide-enter-active, .bs-slide-leave-active {
  transition: transform 0.35s cubic-bezier(0.32, 0.72, 0, 1);
}

.bs-slide-enter-from, .bs-slide-leave-to {
  transform: translateY(100%);
}

.expand-enter-active, .expand-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.expand-enter-from, .expand-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
