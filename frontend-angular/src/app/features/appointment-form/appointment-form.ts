import {
  AbstractControl,
  FormControl,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Appointment } from '../../core/models';

export const DURATION_CHIPS = [
  { label: '30 Min', minutes: 30 },
  { label: '1 Std', minutes: 60 },
  { label: '1,5 Std', minutes: 90 },
  { label: '2 Std', minutes: 120 },
  { label: '6 Std', minutes: 360 },
  { label: 'Ganztägig', minutes: 1440 },
  { label: '2 Tage', minutes: 2880 },
  { label: '14 Tage', minutes: 20160 },
];

export type DurationUnit = 'minutes' | 'hours' | 'days';

export const DURATION_UNITS: { label: string; value: DurationUnit; multiplier: number }[] = [
  { label: 'Minuten', value: 'minutes', multiplier: 1 },
  { label: 'Stunden', value: 'hours', multiplier: 60 },
  { label: 'Tage', value: 'days', multiplier: 1440 },
];

export const DEFAULT_DURATION_MINUTES = 60;
export const DEFAULT_MINIMAL_ATTENDEES = 5;
export const MIN_ATTENDEES_FLOOR = 0;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 1440;
const MS_PER_MINUTE = 60_000;

export interface AppointmentFormValue {
  name: string;
  start: string;
  /** Gewählter Chip in Minuten; `null`, wenn eine eigene Dauer aktiv ist. */
  durationMinutes: number | null;
  customDuration: boolean;
  customValue: number;
  customUnit: DurationUnit;
  description: string;
  venue: string;
  hasAttendees: boolean;
  minimalAttendees: number;
}

export type AppointmentPayload = Pick<
  Appointment,
  'name' | 'description' | 'start' | 'end' | 'venue' | 'minimal_attendees'
>;

const pad = (n: number) => String(n).padStart(2, '0');

/** Wert für `<input type="datetime-local">` in Ortszeit. */
export function toDatetimeLocal(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Nächste volle Stunde nach `now`. */
export function defaultStart(now: Date): string {
  const start = new Date(now);
  start.setHours(now.getHours() + 1, 0, 0, 0);
  return toDatetimeLocal(start);
}

/** Dauer in Minuten oder `null`, wenn keine gültige Dauer gewählt ist. */
export function effectiveDuration(
  v: Pick<
    AppointmentFormValue,
    'customDuration' | 'durationMinutes' | 'customValue' | 'customUnit'
  >,
): number | null {
  if (v.customDuration) {
    if (!(v.customValue > 0)) return null;
    const unit = DURATION_UNITS.find((u) => u.value === v.customUnit);
    return unit ? v.customValue * unit.multiplier : null;
  }
  return v.durationMinutes;
}

export function durationLabel(minutes: number): string {
  const chip = DURATION_CHIPS.find((c) => c.minutes === minutes);
  if (chip) return chip.label;
  if (minutes % MINUTES_PER_DAY === 0) return `${minutes / MINUTES_PER_DAY} Tage`;
  if (minutes % MINUTES_PER_HOUR === 0) return `${minutes / MINUTES_PER_HOUR} Std`;
  return `${minutes} Min`;
}

const notBlank = (control: AbstractControl<string>): ValidationErrors | null =>
  control.value.trim().length > 0 ? null : { required: true };

const durationValidator = (group: AbstractControl): ValidationErrors | null =>
  effectiveDuration((group as AppointmentForm).getRawValue()) === null ? { duration: true } : null;

export type AppointmentForm = ReturnType<typeof buildAppointmentForm>;

const initialValue = (): AppointmentFormValue => ({
  name: '',
  start: defaultStart(new Date()),
  durationMinutes: DEFAULT_DURATION_MINUTES,
  customDuration: false,
  customValue: DEFAULT_DURATION_MINUTES,
  customUnit: 'minutes',
  description: '',
  venue: '',
  hasAttendees: false,
  minimalAttendees: DEFAULT_MINIMAL_ATTENDEES,
});

/** Setzt das Formular auf die Anfangswerte zurück (Start = nächste volle Stunde). */
export function resetAppointmentForm(form: AppointmentForm): void {
  form.reset(initialValue());
}

/** Reactive Form für Anlegen und Bearbeiten (Port der Refs aus `CreateAppointmentStepper.vue` / `EditAppointmentBottomSheet.vue`). */
export function buildAppointmentForm() {
  const init = initialValue();
  const control = <T>(
    value: T,
    validators: ((c: AbstractControl<T>) => ValidationErrors | null)[] = [],
  ) => new FormControl<T>(value, { nonNullable: true, validators });
  return new FormGroup(
    {
      name: control<string>(init.name, [notBlank]),
      start: control<string>(init.start, [Validators.required]),
      durationMinutes: new FormControl<number | null>(init.durationMinutes),
      customDuration: control<boolean>(init.customDuration),
      customValue: control(init.customValue),
      customUnit: control<DurationUnit>(init.customUnit),
      description: control<string>(init.description),
      venue: control<string>(init.venue),
      hasAttendees: control<boolean>(init.hasAttendees),
      minimalAttendees: control(init.minimalAttendees),
    },
    { validators: [durationValidator] },
  );
}

/** Request-Body für Anlegen/Ändern; `null`, solange Name, Start oder Dauer fehlen. */
export function toPayload(v: AppointmentFormValue): AppointmentPayload | null {
  const duration = effectiveDuration(v);
  const start = v.start ? new Date(v.start) : null;
  if (!v.name.trim() || !start || Number.isNaN(start.getTime()) || duration === null) return null;
  return {
    name: v.name.trim(),
    description: v.description.trim() || null,
    start: start.toISOString(),
    end: new Date(start.getTime() + duration * MS_PER_MINUTE).toISOString(),
    venue: v.venue.trim() || null,
    minimal_attendees: v.hasAttendees ? v.minimalAttendees : 0,
  };
}

/** Füllt das Formular aus einem bestehenden Termin; eine nicht zu den Chips passende Dauer wird zur eigenen Dauer. */
export function patchFormFromAppointment(
  form: AppointmentForm,
  appt: Pick<Appointment, 'name' | 'description' | 'venue' | 'minimal_attendees' | 'start' | 'end'>,
): void {
  const start = new Date(appt.start);
  const minutes = Math.round((new Date(appt.end).getTime() - start.getTime()) / MS_PER_MINUTE);
  const base = {
    name: appt.name,
    description: appt.description ?? '',
    venue: appt.venue ?? '',
    start: toDatetimeLocal(start),
    hasAttendees: appt.minimal_attendees > 0,
    minimalAttendees:
      appt.minimal_attendees > 0 ? appt.minimal_attendees : DEFAULT_MINIMAL_ATTENDEES,
  };
  if (DURATION_CHIPS.some((c) => c.minutes === minutes)) {
    form.patchValue({ ...base, durationMinutes: minutes, customDuration: false });
  } else if (minutes % MINUTES_PER_DAY === 0) {
    form.patchValue({
      ...base,
      durationMinutes: null,
      customDuration: true,
      customUnit: 'days',
      customValue: minutes / MINUTES_PER_DAY,
    });
  } else if (minutes % MINUTES_PER_HOUR === 0) {
    form.patchValue({
      ...base,
      durationMinutes: null,
      customDuration: true,
      customUnit: 'hours',
      customValue: minutes / MINUTES_PER_HOUR,
    });
  } else {
    form.patchValue({
      ...base,
      durationMinutes: null,
      customDuration: true,
      customUnit: 'minutes',
      customValue: minutes,
    });
  }
}
