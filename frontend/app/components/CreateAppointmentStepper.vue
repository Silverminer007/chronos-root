<script setup lang="ts">
import { useAppointmentsStore } from '~/stores/appointments'
import { useToast } from 'primevue/usetoast'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; 'created': [id: number] }>()

const appointmentStore = useAppointmentsStore()
const toast = useToast()

const step = ref(1)
const TOTAL_STEPS = 6
const saving = ref(false)
const picker = ref<{
  getSelectedUsers: () => { id: number; role: string }[]
  getSelectedGroups: () => { id: number; role: string }[]
} | null>(null)
const stepDirection = ref<'forward' | 'back'>('forward')

const titleInput = ref<HTMLInputElement | null>(null)
const descriptionInput = ref<HTMLTextAreaElement | null>(null)
const venueInput = ref<HTMLInputElement | null>(null)

const name = ref('')
const startStr = ref('')
const selectedDuration = ref<number | null>(60)
const showCustomDuration = ref(false)
const customDurationValue = ref(60)
const customDurationUnit = ref<'minutes' | 'hours' | 'days'>('minutes')
const hasAttendees = ref(false)
const minimalAttendees = ref(5)
const description = ref('')
const venue = ref('')

const TODAY_CHIPS = [
  { label: 'Heute', days: 0 },
  { label: 'Morgen', days: 1 },
  { label: 'Übermorgen', days: 2 },
]

const STEP_LABELS = ['Name', 'Datum & Zeit', 'Dauer', 'Beschreibung', 'Ort', 'Teilnehmer']

function initDefaults() {
  step.value = 1
  name.value = ''
  description.value = ''
  venue.value = ''
  hasAttendees.value = false
  minimalAttendees.value = 5
  selectedDuration.value = 60
  showCustomDuration.value = false
  customDurationValue.value = 60
  customDurationUnit.value = 'minutes'
  invitedUserCount.value = 0
  invitedGroupMemberCount.value = 0

  const now = new Date()
  const start = new Date(now)
  start.setHours(now.getHours() + 1, 0, 0, 0)
  startStr.value = toDatetimeLocal(start)
}

watch(() => props.modelValue, (val) => {
  if (val) {
    initDefaults()
    nextTick(() => titleInput.value?.focus())
  }
})

watch(step, (val) => {
  if (val === 4) nextTick(() => descriptionInput.value?.focus())
  if (val === 5) nextTick(() => venueInput.value?.focus())
})

function setDatePart(daysFromNow: number) {
  const current = startStr.value ? new Date(startStr.value) : new Date()
  const target = new Date()
  target.setDate(target.getDate() + daysFromNow)
  target.setHours(current.getHours(), current.getMinutes(), 0, 0)
  startStr.value = toDatetimeLocal(target)
}

const startDate = computed(() => startStr.value ? new Date(startStr.value) : null)

const effectiveDuration = computed(() => {
  if (showCustomDuration.value) {
    if (customDurationValue.value <= 0) return null
    const unit = DURATION_UNITS.find(u => u.value === customDurationUnit.value)!
    return customDurationValue.value * unit.multiplier
  }
  return selectedDuration.value
})

const computedEnd = computed(() => {
  if (!startDate.value || !effectiveDuration.value) return null
  return new Date(startDate.value.getTime() + effectiveDuration.value * 60000)
})

const canProceed = computed(() => {
  if (step.value === 1) return name.value.trim().length > 0
  if (step.value === 2) return startDate.value !== null
  if (step.value === 3) return effectiveDuration.value !== null
  return true
})

// Summary display values for the desktop left panel
const startDateLabel = computed(() => {
  if (!startDate.value) return null
  return startDate.value.toLocaleDateString('de-DE', {
    weekday: 'short', day: 'numeric', month: 'short',
    hour: '2-digit', minute: '2-digit',
  })
})

const durationLabel = computed(() => {
  if (!effectiveDuration.value) return null
  const chip = DURATION_CHIPS.find(c => c.minutes === effectiveDuration.value)
  if (chip) return chip.label
  const m = effectiveDuration.value
  if (m % 1440 === 0) return `${m / 1440} Tage`
  if (m % 60 === 0) return `${m / 60} Std`
  return `${m} Min`
})

const invitedUserCount = ref(0)
const invitedGroupMemberCount = ref(0)

function onPickerChange(userCount: number, groupMemberCount: number) {
  invitedUserCount.value = userCount
  invitedGroupMemberCount.value = groupMemberCount
}

const participantsLabel = computed(() => {
  const total = invitedUserCount.value + invitedGroupMemberCount.value
  return total > 0 ? `${total} Eingeladene` : null
})

function selectDuration(minutes: number) {
  selectedDuration.value = minutes
  showCustomDuration.value = false
}

function toggleCustomDuration() {
  showCustomDuration.value = !showCustomDuration.value
  if (showCustomDuration.value) selectedDuration.value = null
}

function goNext() {
  if (!canProceed.value) return
  if (step.value < TOTAL_STEPS) {
    stepDirection.value = 'forward'
    step.value++
  } else {
    submit()
  }
}

function goBack() {
  if (step.value > 1) {
    stepDirection.value = 'back'
    step.value--
  }
}

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  if (saving.value) return
  saving.value = true
  try {
    const result = await appointmentStore.createAppointment({
      name: name.value.trim(),
      description: description.value.trim() || null,
      start: startDate.value!.toISOString(),
      end: computedEnd.value!.toISOString(),
      venue: venue.value.trim() || null,
      minimal_attendees: hasAttendees.value ? minimalAttendees.value : 0
    })

    const users = picker.value?.getSelectedUsers() ?? []
    const groups = picker.value?.getSelectedGroups() ?? []
    let failedCount = 0
    for (const u of users) {
      await appointmentStore.addParticipant(result.id, u.id, u.role).catch(() => { failedCount++ })
    }
    for (const g of groups) {
      await appointmentStore.addGroupParticipant(result.id, g.id, g.role).catch(() => { failedCount++ })
    }
    if (failedCount > 0) {
      toast.add({ severity: 'warn', summary: 'Teilweise erfolgreich', detail: `Termin erstellt, aber ${failedCount} Einladung${failedCount === 1 ? '' : 'en'} konnte${failedCount === 1 ? '' : 'n'} nicht gesendet werden.`, life: 5000 })
    }

    close()
    emit('created', result.id)
  } catch {
    toast.add({ severity: 'error', summary: 'Fehler', detail: 'Termin konnte nicht erstellt werden', life: 3000 })
  } finally {
    saving.value = false
  }
}

// Swipe on mobile
const touchStartX = ref(0)
function onTouchStart(e: TouchEvent) { touchStartX.value = e.touches[0]?.clientX ?? 0 }
function onTouchEnd(e: TouchEvent) {
  const dx = (e.changedTouches[0]?.clientX ?? 0) - touchStartX.value
  if (Math.abs(dx) < 50) return
  if (dx < 0 && canProceed.value) goNext()
  else if (dx > 0) goBack()
}
</script>

<template>
  <Transition name="stepper-fade">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex flex-col lg:flex-row bg-white dark:bg-neutral-900"
      @touchstart="onTouchStart"
      @touchend="onTouchEnd"
    >

      <!-- ── Left panel (desktop only) ── -->
      <div class="hidden lg:flex flex-col w-[360px] xl:w-[400px] flex-shrink-0 bg-slate-50 dark:bg-neutral-800/60 border-r border-gray-200 dark:border-neutral-700 p-8 xl:p-10 overflow-y-auto">

        <p class="text-xs font-semibold text-gray-400 dark:text-neutral-500 uppercase tracking-widest mb-6">Neuer Termin</p>

        <!-- Appointment name -->
        <h2
          class="text-3xl xl:text-4xl font-bold leading-tight mb-8 transition-all duration-300"
          :class="name ? 'text-gray-900 dark:text-white' : 'text-gray-200 dark:text-neutral-700'"
        >
          {{ name || 'Terminname' }}
        </h2>

        <!-- Summary — only rendered when filled -->
        <div class="space-y-3 mb-auto text-sm text-gray-500 dark:text-gray-400">
          <div v-if="startDateLabel" class="flex items-center gap-2.5">
            <Icon name="lucide:calendar" class="shrink-0 text-gray-400 dark:text-neutral-500" />
            <span>{{ startDateLabel }}</span>
          </div>
          <div v-if="durationLabel" class="flex items-center gap-2.5">
            <Icon name="lucide:timer" class="shrink-0 text-gray-400 dark:text-neutral-500" />
            <span>{{ durationLabel }}</span>
          </div>
          <div v-if="description" class="flex items-start gap-2.5">
            <Icon name="lucide:file-text" class="shrink-0 mt-0.5 text-gray-400 dark:text-neutral-500" />
            <span class="line-clamp-2">{{ description }}</span>
          </div>
          <div v-if="venue" class="flex items-center gap-2.5">
            <Icon name="lucide:map-pin" class="shrink-0 text-gray-400 dark:text-neutral-500" />
            <span>{{ venue }}</span>
          </div>
          <div v-if="hasAttendees" class="flex items-center gap-2.5">
            <Icon name="lucide:users" class="shrink-0 text-gray-400 dark:text-neutral-500" />
            <span>Mindestens {{ minimalAttendees }} Teilnehmer</span>
          </div>
          <div v-if="participantsLabel" class="flex items-center gap-2.5">
            <Icon name="lucide:user-check" class="shrink-0 text-gray-400 dark:text-neutral-500" />
            <span>{{ participantsLabel }}</span>
          </div>
        </div>

        <!-- Step list -->
        <div class="mt-10 space-y-2">
          <div v-for="(label, i) in STEP_LABELS" :key="i" class="flex items-center gap-3">
            <div
              class="w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 transition-all duration-300"
              :class="i + 1 < step
                ? 'bg-purple-100 dark:bg-purple-900/40 text-purple-500 dark:text-purple-400'
                : i + 1 === step
                  ? 'bg-purple-600 text-white'
                  : 'bg-gray-200 dark:bg-neutral-700 text-gray-400 dark:text-neutral-500'"
            >
              <Icon v-if="i + 1 < step" name="lucide:check" class="text-[10px]" />
              <span v-else class="text-[10px] font-semibold">{{ i + 1 }}</span>
            </div>
            <span
              class="text-sm transition-all duration-300"
              :class="i + 1 < step
                ? 'text-gray-400 dark:text-neutral-500'
                : i + 1 === step
                  ? 'text-gray-900 dark:text-white font-semibold'
                  : 'text-gray-300 dark:text-neutral-600'"
            >{{ label }}</span>
          </div>
        </div>
      </div>

      <!-- ── Right panel ── -->
      <div class="flex flex-col flex-1 min-h-0">

        <!-- Top bar -->
        <div class="flex items-center justify-between px-5 pt-4 pb-4 border-b border-gray-100 dark:border-neutral-800 flex-shrink-0">
          <button
            type="button"
            class="w-10 h-10 rounded-full flex items-center justify-center text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-neutral-800 transition-colors"
            @click="step > 1 ? goBack() : close()"
          >
            <Icon :name="step > 1 ? 'lucide:arrow-left' : 'lucide:x'" class="text-xl" />
          </button>

          <!-- Progress dots — mobile only -->
          <div class="flex items-center gap-2 lg:hidden">
            <div
              v-for="s in TOTAL_STEPS"
              :key="s"
              class="rounded-full transition-all duration-300"
              :class="s === step
                ? 'w-6 h-2 bg-purple-600'
                : s < step
                  ? 'w-2 h-2 bg-purple-400'
                  : 'w-2 h-2 bg-gray-200 dark:bg-neutral-700'"
            />
          </div>

          <!-- Current step label — desktop only -->
          <span class="hidden lg:block text-sm font-semibold text-gray-700 dark:text-gray-200">
            {{ STEP_LABELS[step - 1] }}
          </span>

          <span class="text-sm font-medium text-gray-400 dark:text-neutral-500">{{ step }} / {{ TOTAL_STEPS }}</span>
        </div>

        <!-- Step content -->
        <div class="flex-1 overflow-y-auto">
          <Transition :name="stepDirection === 'forward' ? 'step-fwd' : 'step-back'" mode="out-in">

            <!-- Step 1: Title -->
            <div v-if="step === 1" key="step1" class="flex flex-col items-center justify-center min-h-full px-8 py-12 text-center">
              <div class="w-16 h-16 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-2xl flex items-center justify-center mb-8">
                <Icon name="lucide:type" class="text-purple-600 dark:text-purple-400 text-2xl" />
              </div>
              <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Wie heißt der Termin?</h1>
              <p class="text-gray-500 dark:text-gray-400 mb-10">Gib deinem Termin einen Namen.</p>
              <input
                ref="titleInput"
                v-model="name"
                type="text"
                placeholder="z.B. Sommerfest 2025"
                class="w-full max-w-sm text-center text-2xl font-semibold bg-transparent border-0 border-b-2 border-gray-200 dark:border-neutral-600 focus:border-purple-500 dark:focus:border-purple-400 outline-none pb-3 text-gray-900 dark:text-white placeholder:text-gray-300 dark:placeholder:text-neutral-600 transition-colors"
                @keydown.enter="goNext"
              />
            </div>

            <!-- Step 2: When -->
            <div v-else-if="step === 2" key="step2" class="flex flex-col items-center justify-center min-h-full px-8 py-12 text-center">
              <div class="w-16 h-16 bg-linear-to-br from-blue-100 to-cyan-100 dark:from-blue-900/30 dark:to-cyan-900/30 rounded-2xl flex items-center justify-center mb-8">
                <Icon name="lucide:calendar" class="text-blue-600 dark:text-blue-400 text-2xl" />
              </div>
              <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Wann?</h1>
              <p class="text-gray-500 dark:text-gray-400 mb-8">Wähle Datum und Uhrzeit.</p>

              <div class="flex gap-2 mb-6">
                <button
                  v-for="chip in TODAY_CHIPS"
                  :key="chip.days"
                  type="button"
                  class="px-4 py-2 rounded-full text-sm font-medium bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 hover:bg-purple-100 dark:hover:bg-purple-900/30 hover:text-purple-700 dark:hover:text-purple-300 transition-colors"
                  @click="setDatePart(chip.days)"
                >
                  {{ chip.label }}
                </button>
              </div>

              <input
                v-model="startStr"
                type="datetime-local"
                class="px-5 py-4 rounded-2xl border-2 border-gray-200 dark:border-neutral-700 bg-gray-50 dark:bg-neutral-800 text-gray-900 dark:text-white text-lg focus:outline-none focus:border-purple-500 transition-all w-full max-w-sm"
              />
            </div>

            <!-- Step 3: Duration -->
            <div v-else-if="step === 3" key="step3" class="flex flex-col items-center justify-center min-h-full px-8 py-12 text-center">
              <div class="w-16 h-16 bg-linear-to-br from-amber-100 to-orange-100 dark:from-amber-900/30 dark:to-orange-900/30 rounded-2xl flex items-center justify-center mb-8">
                <Icon name="lucide:timer" class="text-amber-600 dark:text-amber-400 text-2xl" />
              </div>
              <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Wie lange?</h1>
              <p class="text-gray-500 dark:text-gray-400 mb-8">Wähle die Dauer des Termins.</p>

              <div class="grid grid-cols-2 gap-3 w-full max-w-sm">
                <button
                  v-for="chip in DURATION_CHIPS"
                  :key="chip.minutes"
                  type="button"
                  class="py-4 rounded-2xl text-base font-semibold transition-all"
                  :class="!showCustomDuration && selectedDuration === chip.minutes
                    ? 'bg-purple-600 text-white shadow-lg scale-[1.02]'
                    : 'bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 hover:bg-purple-50 dark:hover:bg-purple-900/20 hover:text-purple-700 dark:hover:text-purple-300'"
                  @click="selectDuration(chip.minutes)"
                >
                  {{ chip.label }}
                </button>

                <button
                  type="button"
                  class="py-4 rounded-2xl text-base font-semibold transition-all"
                  :class="showCustomDuration
                    ? 'bg-purple-600 text-white shadow-lg scale-[1.02] col-span-2'
                    : 'bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 hover:bg-purple-50 dark:hover:bg-purple-900/20'"
                  @click="toggleCustomDuration"
                >
                  Eigene Dauer
                </button>
              </div>

              <Transition name="step-fwd">
                <div v-if="showCustomDuration" class="mt-5 w-full max-w-sm space-y-3">
                  <div class="flex gap-2">
                    <button
                      v-for="unit in DURATION_UNITS"
                      :key="unit.value"
                      type="button"
                      class="flex-1 py-2.5 rounded-xl text-sm font-semibold transition-all"
                      :class="customDurationUnit === unit.value
                        ? 'bg-purple-600 text-white shadow-sm'
                        : 'bg-gray-100 dark:bg-neutral-800 text-gray-600 dark:text-gray-400 hover:bg-purple-50 dark:hover:bg-purple-900/20'"
                      @click="customDurationUnit = unit.value"
                    >
                      {{ unit.label }}
                    </button>
                  </div>
                  <input
                    v-model.number="customDurationValue"
                    type="number"
                    min="1"
                    class="w-full px-4 py-3 rounded-xl border-2 border-purple-300 dark:border-purple-600 bg-purple-50 dark:bg-purple-900/20 text-gray-900 dark:text-white text-center text-lg font-semibold focus:outline-none focus:border-purple-500"
                  />
                </div>
              </Transition>
            </div>

            <!-- Step 4: Description -->
            <div v-else-if="step === 4" key="step4" class="flex flex-col items-center justify-center min-h-full px-8 py-12 text-center">
              <div class="w-16 h-16 bg-linear-to-br from-violet-100 to-purple-100 dark:from-violet-900/30 dark:to-purple-900/30 rounded-2xl flex items-center justify-center mb-8">
                <Icon name="lucide:file-text" class="text-violet-600 dark:text-violet-400 text-2xl" />
              </div>
              <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Beschreibung</h1>
              <p class="text-gray-500 dark:text-gray-400 mb-8">Optional – überspringe einfach mit „Weiter".</p>
              <textarea
                ref="descriptionInput"
                v-model="description"
                rows="5"
                placeholder="Was sollen die Teilnehmer wissen?"
                class="w-full max-w-sm px-4 py-3 rounded-2xl border-2 border-gray-200 dark:border-neutral-700 bg-gray-50 dark:bg-neutral-800 text-gray-900 dark:text-white focus:outline-none focus:border-purple-500 transition-all resize-none"
              />
            </div>

            <!-- Step 5: Venue -->
            <div v-else-if="step === 5" key="step5" class="flex flex-col items-center justify-center min-h-full px-8 py-12 text-center">
              <div class="w-16 h-16 bg-linear-to-br from-rose-100 to-pink-100 dark:from-rose-900/30 dark:to-pink-900/30 rounded-2xl flex items-center justify-center mb-8">
                <Icon name="lucide:map-pin" class="text-rose-600 dark:text-rose-400 text-2xl" />
              </div>
              <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-2">Wo findet es statt?</h1>
              <p class="text-gray-500 dark:text-gray-400 mb-8">Optional – überspringe einfach mit „Weiter".</p>
              <input
                ref="venueInput"
                v-model="venue"
                type="text"
                placeholder="z.B. Vereinsheim"
                class="w-full max-w-sm text-center text-xl bg-transparent border-0 border-b-2 border-gray-200 dark:border-neutral-600 focus:border-purple-500 dark:focus:border-purple-400 outline-none pb-3 text-gray-900 dark:text-white placeholder:text-gray-300 dark:placeholder:text-neutral-600 transition-colors"
                @keydown.enter="goNext"
              />
            </div>

            <!-- Step 6: Attendees + participants -->
            <div v-else-if="step === 6" key="step6" class="flex flex-col items-center min-h-full px-6 py-8 space-y-6">
              <div class="text-center">
                <div class="w-16 h-16 bg-linear-to-br from-green-100 to-teal-100 dark:from-green-900/30 dark:to-teal-900/30 rounded-2xl flex items-center justify-center mb-4 mx-auto">
                  <Icon name="lucide:users" class="text-green-600 dark:text-green-400 text-2xl" />
                </div>
                <h1 class="text-2xl font-bold text-gray-900 dark:text-white mb-1">Teilnehmer</h1>
                <p class="text-gray-500 dark:text-gray-400 text-sm">Mindestanzahl und Einladungen festlegen.</p>
              </div>

              <div class="w-full max-w-sm">
                <p class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3 text-center">Mindestanzahl Teilnehmer</p>
                <div class="flex gap-3 justify-center mb-3">
                  <button
                    type="button"
                    class="flex-1 py-3.5 rounded-2xl text-base font-bold transition-all max-w-[120px]"
                    :class="hasAttendees ? 'bg-green-600 text-white shadow-lg' : 'bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 hover:bg-green-50 dark:hover:bg-green-900/20'"
                    @click="hasAttendees = true"
                  >
                    Ja
                  </button>
                  <button
                    type="button"
                    class="flex-1 py-3.5 rounded-2xl text-base font-bold transition-all max-w-[120px]"
                    :class="!hasAttendees ? 'bg-gray-800 dark:bg-neutral-200 text-white dark:text-neutral-900 shadow-lg' : 'bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-neutral-700'"
                    @click="hasAttendees = false"
                  >
                    Nein
                  </button>
                </div>

                <Transition name="step-fwd">
                  <div v-if="hasAttendees" class="flex items-center justify-center gap-5 py-2">
                    <button
                      type="button"
                      class="w-11 h-11 rounded-full bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 flex items-center justify-center font-bold text-2xl hover:bg-gray-200 dark:hover:bg-neutral-700 transition-colors"
                      @click="minimalAttendees = Math.max(0, minimalAttendees - 1)"
                    >−</button>
                    <span class="w-10 text-center font-bold text-4xl text-gray-900 dark:text-white">{{ minimalAttendees }}</span>
                    <button
                      type="button"
                      class="w-11 h-11 rounded-full bg-gray-100 dark:bg-neutral-800 text-gray-700 dark:text-gray-300 flex items-center justify-center font-bold text-2xl hover:bg-gray-200 dark:hover:bg-neutral-700 transition-colors"
                      @click="minimalAttendees++"
                    >+</button>
                  </div>
                </Transition>
              </div>

              <div class="w-full max-w-sm">
                <p class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3 text-center">Personen &amp; Gruppen einladen</p>
                <AppointmentParticipantPicker ref="picker" @change="onPickerChange" />
              </div>
            </div>

          </Transition>
        </div>

        <!-- Bottom nav -->
        <div class="flex items-center gap-4 px-5 py-4 border-t border-gray-100 dark:border-neutral-800 flex-shrink-0" style="padding-bottom: max(1rem, env(safe-area-inset-bottom))">
          <button
            v-if="step > 1"
            type="button"
            class="px-5 py-3.5 rounded-2xl font-semibold text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-neutral-800 hover:bg-gray-200 dark:hover:bg-neutral-700 transition-colors"
            @click="goBack"
          >
            Zurück
          </button>
          <button
            v-else
            type="button"
            class="px-5 py-3.5 rounded-2xl font-semibold text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-neutral-800 hover:bg-gray-200 dark:hover:bg-neutral-700 transition-colors"
            @click="close"
          >
            Abbrechen
          </button>

          <button
            type="button"
            :disabled="!canProceed || saving"
            class="flex-1 flex items-center justify-center gap-2 py-3.5 rounded-2xl font-bold text-white transition-all disabled:opacity-50"
            :class="canProceed && !saving
              ? 'bg-linear-to-r from-purple-600 to-pink-500 hover:from-purple-700 hover:to-pink-600 shadow-lg active:scale-[0.98]'
              : 'bg-gray-200 dark:bg-neutral-700 text-gray-400 dark:text-neutral-500 cursor-not-allowed'"
            @click="goNext"
          >
            <Icon v-if="saving" name="lucide:loader-circle" class="animate-spin" />
            <span>{{ step < TOTAL_STEPS ? 'Weiter' : (saving ? 'Erstelle…' : 'Erstellen') }}</span>
            <Icon v-if="!saving && step < TOTAL_STEPS" name="lucide:arrow-right" />
            <Icon v-if="!saving && step === TOTAL_STEPS" name="lucide:check" />
          </button>
        </div>

      </div>
    </div>
  </Transition>
</template>

<style scoped>
.stepper-fade-enter-active { transition: opacity 0.2s ease, transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); }
.stepper-fade-leave-active { transition: opacity 0.2s ease, transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); }
.stepper-fade-enter-from { opacity: 0; transform: translateY(30px); }
.stepper-fade-leave-to { opacity: 0; transform: translateY(30px); }

.step-fwd-enter-active, .step-back-enter-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.step-fwd-leave-active, .step-back-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }

.step-fwd-enter-from { opacity: 0; transform: translateX(24px); }
.step-fwd-leave-to { opacity: 0; transform: translateX(-24px); }

.step-back-enter-from { opacity: 0; transform: translateX(-24px); }
.step-back-leave-to { opacity: 0; transform: translateX(24px); }
</style>
