import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { startWith } from 'rxjs';
import { Appointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import {
  DURATION_CHIPS,
  DURATION_UNITS,
  MIN_ATTENDEES_FLOOR,
  DEFAULT_DURATION_MINUTES,
  buildAppointmentForm,
  patchFormFromAppointment,
  toPayload,
} from './appointment-form';

/** Bottom-Sheet zum Bearbeiten eines Termins (Port von `EditAppointmentBottomSheet.vue`). */
@Component({
  selector: 'app-edit-appointment-bottom-sheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './edit-appointment-bottom-sheet.component.html',
  styleUrl: './edit-appointment-bottom-sheet.component.scss',
})
export class EditAppointmentBottomSheetComponent {
  private readonly store = inject(AppointmentStore);
  private readonly toast = inject(ToastService);

  /** Sichtbarkeit (`v-model` im Nuxt-Original). */
  readonly open = model(false);
  readonly appointment = input.required<Appointment>();
  readonly saved = output<void>();

  protected readonly form = buildAppointmentForm();
  private readonly changes = toSignal(this.form.valueChanges.pipe(startWith(undefined)));
  protected readonly saving = signal(false);

  protected readonly durationChips = DURATION_CHIPS;
  protected readonly durationUnits = DURATION_UNITS;

  private readonly payload = computed(() => {
    this.changes();
    return toPayload(this.form.getRawValue());
  });
  protected readonly isValid = computed(() => this.payload() !== null);
  protected readonly hasChanges = computed(() => {
    const next = this.payload();
    if (!next) return false;
    const current = this.appointment();
    return (
      next.name !== current.name ||
      next.description !== (current.description || null) ||
      next.venue !== (current.venue || null) ||
      new Date(next.start).getTime() !== new Date(current.start).getTime() ||
      new Date(next.end).getTime() !== new Date(current.end).getTime() ||
      next.minimal_attendees !== current.minimal_attendees
    );
  });

  constructor() {
    effect(() => {
      if (this.open()) untracked(() => patchFormFromAppointment(this.form, this.appointment()));
    });
  }

  protected selectDuration(minutes: number): void {
    this.form.patchValue({ durationMinutes: minutes, customDuration: false });
  }

  protected toggleCustomDuration(): void {
    const custom = !this.form.controls.customDuration.value;
    this.form.patchValue({
      customDuration: custom,
      durationMinutes: custom ? null : DEFAULT_DURATION_MINUTES,
    });
  }

  protected toggleAttendees(): void {
    this.form.controls.hasAttendees.setValue(!this.form.controls.hasAttendees.value);
  }

  protected changeMinimalAttendees(delta: number): void {
    const control = this.form.controls.minimalAttendees;
    control.setValue(Math.max(MIN_ATTENDEES_FLOOR, control.value + delta));
  }

  protected close(): void {
    this.open.set(false);
  }

  protected async submit(): Promise<void> {
    const payload = this.payload();
    if (!payload || !this.hasChanges() || this.saving()) return;
    const id = this.appointment().id;
    this.saving.set(true);
    try {
      await this.store.updateAppointment(id, payload);
      await this.store.fetchAppointment(id);
      this.toast.success('Änderungen gespeichert');
      this.close();
      this.saved.emit();
    } catch {
      this.toast.error('Fehler', 'Änderungen konnten nicht gespeichert werden');
    } finally {
      this.saving.set(false);
    }
  }
}
