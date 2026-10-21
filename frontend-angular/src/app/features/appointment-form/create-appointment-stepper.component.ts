import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  model,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { startWith } from 'rxjs';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import {
  AppointmentFormValue,
  DURATION_CHIPS,
  DURATION_UNITS,
  MIN_ATTENDEES_FLOOR,
  buildAppointmentForm,
  durationLabel,
  effectiveDuration,
  resetAppointmentForm,
  toDatetimeLocal,
  toPayload,
} from './appointment-form';
import {
  AppointmentParticipantPickerComponent,
  PickerChange,
} from './appointment-participant-picker.component';

const STEP_LABELS = ['Name', 'Datum & Zeit', 'Dauer', 'Beschreibung', 'Ort', 'Teilnehmer'];
const TOTAL_STEPS = STEP_LABELS.length;
const SWIPE_THRESHOLD_PX = 50;
const WARN_TOAST_MS = 5000;
const TODAY_CHIPS = [
  { label: 'Heute', days: 0 },
  { label: 'Morgen', days: 1 },
  { label: 'Übermorgen', days: 2 },
];
const SUMMARY_DATE: Intl.DateTimeFormatOptions = {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
};

/** Vollbild-Assistent zum Anlegen eines Termins in sechs Schritten (Port von `CreateAppointmentStepper.vue`). */
@Component({
  selector: 'app-create-appointment-stepper',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, AppointmentParticipantPickerComponent],
  templateUrl: './create-appointment-stepper.component.html',
  styleUrl: './create-appointment-stepper.component.scss',
})
export class CreateAppointmentStepperComponent {
  private readonly store = inject(AppointmentStore);
  private readonly toast = inject(ToastService);

  /** Sichtbarkeit (`v-model` im Nuxt-Original). */
  readonly open = model(false);
  /** ID des neu angelegten Termins. */
  readonly created = output<number>();

  protected readonly form = buildAppointmentForm();
  private readonly changes = toSignal(this.form.valueChanges.pipe(startWith(undefined)));
  protected readonly step = signal(1);
  protected readonly saving = signal(false);
  protected readonly invited = signal<PickerChange>({ userCount: 0, groupMemberCount: 0 });
  private readonly picker = viewChild(AppointmentParticipantPickerComponent);

  protected readonly stepLabels = STEP_LABELS;
  protected readonly steps = STEP_LABELS.map((_, i) => i + 1);
  protected readonly totalSteps = TOTAL_STEPS;
  protected readonly todayChips = TODAY_CHIPS;
  protected readonly durationChips = DURATION_CHIPS;
  protected readonly durationUnits = DURATION_UNITS;

  protected readonly formValue = computed<AppointmentFormValue>(() => {
    this.changes();
    return this.form.getRawValue();
  });
  protected readonly canProceed = computed(() => {
    const v = this.formValue();
    switch (this.step()) {
      case 1:
        return v.name.trim().length > 0;
      case 2:
        return !!v.start;
      case 3:
        return effectiveDuration(v) !== null;
      default:
        return true;
    }
  });
  protected readonly startLabel = computed(() => {
    const { start } = this.formValue();
    return start ? new Date(start).toLocaleDateString('de-DE', SUMMARY_DATE) : null;
  });
  protected readonly durationText = computed(() => {
    const minutes = effectiveDuration(this.formValue());
    return minutes ? durationLabel(minutes) : null;
  });
  protected readonly participantsLabel = computed(() => {
    const { userCount, groupMemberCount } = this.invited();
    const total = userCount + groupMemberCount;
    return total > 0 ? `${total} Eingeladene` : null;
  });

  private direction: 'forward' | 'back' = 'forward';
  private touchStartX = 0;

  constructor() {
    effect(() => {
      if (this.open()) untracked(() => this.init());
    });
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    effect(() => {
      const step = this.step();
      if (!this.open()) return;
      const focusTarget = { 1: '#stepper-name', 4: '#stepper-description', 5: '#stepper-venue' }[
        step
      ];
      if (focusTarget)
        queueMicrotask(() => host.nativeElement.querySelector<HTMLElement>(focusTarget)?.focus());
    });
  }

  protected setToday(daysFromNow: number): void {
    const { start } = this.form.getRawValue();
    const current = start ? new Date(start) : new Date();
    const target = new Date();
    target.setDate(target.getDate() + daysFromNow);
    target.setHours(current.getHours(), current.getMinutes(), 0, 0);
    this.form.controls.start.setValue(toDatetimeLocal(target));
  }

  protected selectDuration(minutes: number): void {
    this.form.patchValue({ durationMinutes: minutes, customDuration: false });
  }

  protected toggleCustomDuration(): void {
    const custom = !this.form.controls.customDuration.value;
    this.form.patchValue({ customDuration: custom, ...(custom ? { durationMinutes: null } : {}) });
  }

  protected setAttendees(has: boolean): void {
    this.form.controls.hasAttendees.setValue(has);
  }

  protected changeMinimalAttendees(delta: number): void {
    const control = this.form.controls.minimalAttendees;
    control.setValue(Math.max(MIN_ATTENDEES_FLOOR, control.value + delta));
  }

  protected onPickerChange(change: PickerChange): void {
    this.invited.set(change);
  }

  protected goNext(): void {
    if (!this.canProceed()) return;
    if (this.step() < TOTAL_STEPS) {
      this.direction = 'forward';
      this.step.update((s) => s + 1);
    } else {
      void this.submit();
    }
  }

  protected goBack(): void {
    if (this.step() > 1) {
      this.direction = 'back';
      this.step.update((s) => s - 1);
    }
  }

  protected close(): void {
    this.open.set(false);
  }

  protected get stepClass(): string {
    return this.direction === 'forward' ? 'step-fwd' : 'step-back';
  }

  protected onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0]?.clientX ?? 0;
  }

  protected onTouchEnd(event: TouchEvent): void {
    const dx = (event.changedTouches[0]?.clientX ?? 0) - this.touchStartX;
    if (Math.abs(dx) < SWIPE_THRESHOLD_PX) return;
    if (dx < 0) this.goNext();
    else this.goBack();
  }

  private init(): void {
    resetAppointmentForm(this.form);
    this.step.set(1);
    this.saving.set(false);
    this.invited.set({ userCount: 0, groupMemberCount: 0 });
    this.direction = 'forward';
  }

  private async submit(): Promise<void> {
    const payload = toPayload(this.form.getRawValue());
    if (this.saving() || !payload) return;
    this.saving.set(true);
    try {
      const result = await this.store.createAppointment(payload);
      const failed = await this.invite(result.id);
      if (failed > 0) {
        this.toast.add({
          severity: 'warn',
          summary: 'Teilweise erfolgreich',
          detail: `Termin erstellt, aber ${failed} Einladung${failed === 1 ? '' : 'en'} konnte${failed === 1 ? '' : 'n'} nicht gesendet werden.`,
          life: WARN_TOAST_MS,
        });
      }
      this.close();
      this.created.emit(result.id);
    } catch {
      this.toast.error('Fehler', 'Termin konnte nicht erstellt werden');
    } finally {
      this.saving.set(false);
    }
  }

  /** Lädt Personen und Gruppen nacheinander ein und liefert die Zahl der gescheiterten Einladungen. */
  private async invite(appointmentId: number): Promise<number> {
    const picker = this.picker();
    let failed = 0;
    for (const user of picker?.selectedUsers() ?? []) {
      await this.store.addParticipant(appointmentId, user.id, user.role).catch(() => failed++);
    }
    for (const group of picker?.selectedGroups() ?? []) {
      await this.store
        .addGroupParticipant(appointmentId, group.id, group.role)
        .catch(() => failed++);
    }
    return failed;
  }
}
