<template>
  <Dialog
      v-model:visible="isVisible"
      modal
      :dismissableMask="true"
      :closable="true"
      class="w-full max-w-3xl"
  >
    <template #header>
      <div class="flex items-center gap-3">
        <div class="w-12 h-12 bg-linear-to-br from-purple-100 to-pink-100 dark:from-purple-900/30 dark:to-pink-900/30 rounded-xl flex items-center justify-center">
          <Icon name="lucide:pencil" class=" text-purple-600 dark:text-purple-400 text-xl" />
        </div>
        <h2 class="text-xl font-bold text-gray-900 dark:text-white">Termin bearbeiten</h2>
      </div>
    </template>

    <div class="space-y-6 pt-4">
      <!-- Event Name -->
      <div>
        <label for="edit-event-name" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Terminname <span class="text-red-500">*</span>
        </label>
        <InputText
            id="edit-event-name"
            v-model="formData.name"
            placeholder="z.B. Sommerfest 2024"
            class="w-full"
            :class="{ 'p-invalid': errors.name }"
        />
        <small v-if="errors.name" class="text-red-500">{{ errors.name }}</small>
      </div>

      <!-- Description -->
      <div>
        <label for="edit-event-description" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Beschreibung
        </label>
        <Textarea
            id="edit-event-description"
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
          <label for="edit-start-date" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Startdatum & -zeit <span class="text-red-500">*</span>
          </label>
          <DatePicker
              id="edit-start-date"
              v-model="formData.start"
              showTime
              hourFormat="24"
              dateFormat="dd.mm.yy"
              placeholder="Datum & Zeit wählen"
              class="w-full"
              :class="{ 'p-invalid': errors.start }"
          />
          <small v-if="errors.start" class="text-red-500">{{ errors.start }}</small>
        </div>

        <!-- End Date & Time -->
        <div>
          <label for="edit-end-date" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Enddatum & -zeit <span class="text-red-500">*</span>
          </label>
          <DatePicker
              id="edit-end-date"
              v-model="formData.end"
              showTime
              hourFormat="24"
              dateFormat="dd.mm.yy"
              placeholder="Datum & Zeit wählen"
              class="w-full"
              :class="{ 'p-invalid': errors.end }"
              :minDate="formData.start"
          />
          <small v-if="errors.end" class="text-red-500">{{ errors.end }}</small>
        </div>
      </div>

      <!-- Venue -->
      <div>
        <label for="edit-venue" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Veranstaltungsort
        </label>
        <InputText
            id="edit-venue"
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
        <label for="edit-minimal-attendees" class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Mindestanzahl Teilnehmer
        </label>
        <InputNumber
            id="edit-minimal-attendees"
            v-model="formData.minimalAttendees"
            :min="0"
            :max="1000"
            placeholder="z.B. 10"
            class="w-full"
        />
        <small class="text-gray-500 dark:text-gray-400">Optional: Wieviele Teilnehmer werden mindestens benötigt?</small>
      </div>

      <!-- Changes Info -->
      <div v-if="hasChanges" class="p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
        <div class="flex gap-3">
          <Icon name="lucide:info" class=" text-yellow-600 dark:text-yellow-400 mt-0.5" />
          <div class="flex-1">
            <p class="text-sm font-medium text-yellow-900 dark:text-yellow-200">
              Änderungen vorhanden
            </p>
            <p class="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
              Alle Teilnehmer werden über die Änderungen benachrichtigt.
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
            @click="closeDialog"
            :disabled="saving"
        />
        <Button
            label="Änderungen speichern"
            @click="saveChanges"
            :loading="saving"
            :disabled="!isValid || !hasChanges"
        >
          <template #icon>
            <Icon name="lucide:check" class=" mr-2" />
          </template>
        </Button>
      </div>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue';
import {useAppointmentsStore} from '~/stores/appointments';
import {useToast} from 'primevue/usetoast';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import DatePicker from 'primevue/datepicker';
import InputNumber from 'primevue/inputnumber';
import Button from 'primevue/button';
import type {Appointment} from '~/types';

interface Props {
  visible: boolean;
  appointment: Appointment | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:visible': [value: boolean];
  saved: [];
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
  minimalAttendees: null as number | null
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

const hasChanges = computed(() => {
  if (!props.appointment) return false;

  return formData.value.name !== props.appointment.name ||
      formData.value.description !== (props.appointment.description || '') ||
      formData.value.venue !== (props.appointment.venue || '') ||
      formData.value.minimalAttendees !== props.appointment.minimal_attendees ||
      (formData.value.start && formData.value.start.toISOString() !== props.appointment.start) ||
      (formData.value.end && formData.value.end.toISOString() !== props.appointment.end);
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

const saveChanges = async () => {
  if (!validateForm() || !props.appointment) {
    return;
  }

  saving.value = true;

  try {
    const updates = {
      name: formData.value.name.trim(),
      description: formData.value.description.trim() || null,
      start: formData.value.start!.toISOString(),
      end: formData.value.end!.toISOString(),
      venue: formData.value.venue.trim() || null,
      minimal_attendees: formData.value.minimalAttendees || 0
    };

    await appointmentStore.updateAppointment(props.appointment.id, updates);

    toast.add({
      severity: 'success',
      summary: 'Änderungen gespeichert',
      detail: 'Der Termin wurde erfolgreich aktualisiert',
      life: 3000
    });

    // Close dialog
    closeDialog();

    // Emit saved event
    emit('saved');

    // Reload appointment
    await appointmentStore.fetchAppointment(props.appointment.id);

  } catch (err) {
    toast.add({
      severity: 'error',
      summary: 'Fehler',
      detail: 'Änderungen konnten nicht gespeichert werden',
      life: 3000
    });
  } finally {
    saving.value = false;
  }
};

const loadAppointmentData = () => {
  if (!props.appointment) return;

  formData.value = {
    name: props.appointment.name,
    description: props.appointment.description || '',
    start: new Date(props.appointment.start),
    end: new Date(props.appointment.end),
    venue: props.appointment.venue || '',
    minimalAttendees: props.appointment.minimal_attendees || null
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

// Load appointment data when dialog opens
watch(() => props.visible, (newVal) => {
  if (newVal) {
    loadAppointmentData();
  }
});
</script>

<style scoped>
/* Custom styling for invalid inputs */
:deep(.p-invalid) {
  border-color: rgb(239 68 68) !important;
}

:deep(.p-invalid:focus) {
  box-shadow: 0 0 0 0.2rem rgba(239, 68, 68, 0.25) !important;
}
</style>