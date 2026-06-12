<script setup lang="ts">
import {useAppointmentsStore} from '~/stores/appointments';
import {useToast} from 'primevue/usetoast';

interface Props {
  visible: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:visible': [value: boolean];
  created: [];
}>();

const appointmentStore = useAppointmentsStore();
const toast = useToast();

const isVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
});

const formData = ref({
  name: '',
  description: '',
  start: null as Date | null,
  end: null as Date | null,
  venue: '',
  minimalAttendees: null as number | null,
  role: 'RESPONSIBLE' as 'RESPONSIBLE' | 'ATTENDANT'
});

const errors = ref({
  name: '',
  start: '',
  end: ''
});

const saving = ref(false);

const isValid = computed(() => {
  return formData.value.name.trim().length > 0 &&
      formData.value.start !== null &&
      formData.value.end !== null &&
      formData.value.end > formData.value.start;
});

const validateForm = () => {
  errors.value = {
    name: '',
    start: '',
    end: ''
  };

  let valid = true;

  if (!formData.value.name.trim()) {
    errors.value.name = 'Terminname ist erforderlich';
    valid = false;
  }

  if (!formData.value.start) {
    errors.value.start = 'Startdatum ist erforderlich';
    valid = false;
  }

  if (!formData.value.end) {
    errors.value.end = 'Enddatum ist erforderlich';
    valid = false;
  } else if (formData.value.start && formData.value.end <= formData.value.start) {
    errors.value.end = 'Enddatum muss nach dem Startdatum liegen';
    valid = false;
  }

  return valid;
};

const createAppointment = async () => {
  if (!validateForm()) {
    return;
  }

  saving.value = true;

  try {
    const appointmentData = {
      name: formData.value.name.trim(),
      description: formData.value.description.trim() || null,
      start: formData.value.start!.toISOString(),
      end: formData.value.end!.toISOString(),
      venue: formData.value.venue.trim() || null,
      minimal_attendees: formData.value.minimalAttendees || 0
    };

    const newAppointment = await appointmentStore.createAppointment(appointmentData);

    toast.add({
      severity: 'success',
      summary: 'Termin erstellt',
      detail: `"${appointmentData.name}" wurde erfolgreich erstellt`,
      life: 3000
    });

    // Reset form
    resetForm();

    // Close dialog
    closeDialog();

    // Emit created event
    emit('created');

    navigateTo(`/appointment/${newAppointment.id}`);
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Termin konnte nicht erstellt werden',
      life: 3000
    });
  } finally {
    saving.value = false;
  }
};

const resetForm = () => {
  formData.value = {
    name: '',
    description: '',
    start: null,
    end: null,
    venue: '',
    minimalAttendees: null,
    role: 'RESPONSIBLE'
  };
  errors.value = {
    name: '',
    start: '',
    end: ''
  };
};

const closeDialog = () => {
  isVisible.value = false;
};

// Reset form when dialog closes
watch(() => props.visible, (newVal) => {
  if (!newVal) {
    setTimeout(() => {
      resetForm();
    }, 300); // Wait for animation
  } else {
    // Set default start time to now + 1 hour, rounded to next hour
    const now = new Date();
    const start = new Date(now);
    start.setHours(now.getHours() + 1, 0, 0, 0);

    // Set default end time to start + 2 hours
    const end = new Date(start);
    end.setHours(start.getHours() + 2);

    formData.value.start = start;
    formData.value.end = end;
  }
});
</script>

<template>
  <Dialog
      v-model:visible="isVisible"
      modal
      :dismissable-mask="true"
      :closable="true"
      class="w-full max-w-3xl"
  >
    <template #header>
      <div class="flex items-center gap-3">
        <div
            class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center">
          <Icon name="lucide:calendar-plus" class=" text-purple-600 dark:text-purple-400 text-xl" />
        </div>
        <h2 class="text-xl font-bold text-gray-900 dark:text-white">Neuen Termin erstellen</h2>
      </div>
    </template>

    <div class="space-y-6 pt-4">
      <!-- Event Name -->
      <div>
        <label for="event-name" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Terminname <span class="text-red-500">*</span>
        </label>
        <InputText
            id="event-name"
            v-model="formData.name"
            placeholder="z.B. Sommerfest 2024"
            class="w-full"
            :class="{ 'p-invalid': errors.name }"
        />
        <small v-if="errors.name" class="text-red-500">{{ errors.name }}</small>
      </div>

      <!-- Description -->
      <div>
        <label for="event-description" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Beschreibung
        </label>
        <Textarea
            id="event-description"
            v-model="formData.description"
            rows="4"
            placeholder="Beschreibe den Termin..."
            class="w-full"
        />
      </div>

      <!-- Date & Time -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <!-- Start Date & Time -->
        <div>
          <label for="start-date" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Startdatum & -zeit <span class="text-red-500">*</span>
          </label>
          <DatePicker
              id="start-date"
              v-model="formData.start"
              show-time
              hour-format="24"
              date-format="dd.mm.yy"
              placeholder="Datum & Zeit wählen"
              class="w-full"
              :class="{ 'p-invalid': errors.start }"
          />
          <small v-if="errors.start" class="text-red-500">{{ errors.start }}</small>
        </div>

        <!-- End Date & Time -->
        <div>
          <label for="end-date" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Enddatum & -zeit <span class="text-red-500">*</span>
          </label>
          <DatePicker
              id="end-date"
              v-model="formData.end"
              show-time
              hour-format="24"
              date-format="dd.mm.yy"
              placeholder="Datum & Zeit wählen"
              class="w-full"
              :class="{ 'p-invalid': errors.end }"
              :min-date="formData.start"
          />
          <small v-if="errors.end" class="text-red-500">{{ errors.end }}</small>
        </div>
      </div>

      <!-- Venue -->
      <div>
        <label for="venue" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Veranstaltungsort
        </label>
        <InputText
            id="venue"
            v-model="formData.venue"
            placeholder="z.B. Vereinsheim"
            class="w-full"
        >
          <template #prepend>
            <Icon name="lucide:map-pin" />
          </template>
        </InputText>
      </div>

      <!-- Minimal Attendees -->
      <div>
        <label for="minimal-attendees" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Mindestanzahl Teilnehmer
        </label>
        <InputNumber
            id="minimal-attendees"
            v-model="formData.minimalAttendees"
            :min="0"
            :max="1000"
            placeholder="z.B. 10"
            class="w-full"
        />
        <small class="text-gray-500 dark:text-gray-400">Optional: Wieviele Teilnehmer werden mindestens
          benötigt?</small>
      </div>

      <!-- Add Users/Groups Later Info -->
      <div class="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
        <div class="flex gap-3">
          <Icon name="lucide:info" class=" text-blue-600 dark:text-blue-400 mt-0.5" />
          <div class="flex-1">
            <p class="text-sm font-medium text-blue-900 dark:text-blue-200">
              Weitere Teilnehmer hinzufügen
            </p>
            <p class="text-sm text-blue-700 dark:text-blue-300 mt-1">
              Du kannst nach dem Erstellen weitere Personen und Gruppen zum Termin einladen.
            </p>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex flex-col-reverse sm:flex-row justify-end gap-3">
        <Button
            label="Abbrechen"
            severity="secondary"
            :disabled="saving"
            @click="closeDialog"
        />
        <Button
            label="Termin erstellen"
            :loading="saving"
            :disabled="!isValid"
            @click="createAppointment"
        >
          <template #icon>
            <Icon name="lucide:check" class=" mr-2" />
          </template>
        </Button>
      </div>
    </template>
  </Dialog>
</template>

<style scoped>
/* Custom styling for invalid inputs */
:deep(.p-invalid) {
  border-color: rgb(239 68 68) !important;
}

:deep(.p-invalid:focus) {
  box-shadow: 0 0 0 0.2rem rgba(239, 68, 68, 0.25) !important;
}
</style>
