import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { formatDate, formatTime } from '../../core/date-format';
import { Appointment, AppointmentStatus } from '../../core/models';
import { EditAppointmentBottomSheetComponent } from '../appointment-form/edit-appointment-bottom-sheet.component';
import { AppointmentShareService } from './appointment-share.service';
import { IconComponent } from '../../shared/icon/icon.component';

const STATUS: Partial<Record<AppointmentStatus, { label: string; css: string }>> = {
  CANCELLED: { label: 'Abgesagt', css: 'bg-danger-subtle text-danger-emphasis' },
  NOT_ENOUGH_ATTENDEES: {
    label: 'Zu wenig Teilnehmer',
    css: 'bg-warning-subtle text-warning-emphasis',
  },
  DELETED: { label: 'Gelöscht', css: 'bg-secondary-subtle text-secondary-emphasis' },
};

/** Kopfkarte der Detailseite: Name, Status, Zeit, Ort, Beschreibung, Teilen (Port von `AppointmentDetailsCard.vue`). */
@Component({
  selector: 'app-appointment-details-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, EditAppointmentBottomSheetComponent],
  template: `
    <div class="card shadow-sm overflow-hidden">
      <div class="bg-gradient-brand p-4">
        <div class="d-flex align-items-start justify-content-between gap-3 mb-2">
          <h2 class="h3 fw-bold mb-0 text-white">{{ appointment().name }}</h2>
          <div class="d-flex align-items-center gap-2 flex-shrink-0">
            @if (status(); as s) {
              <span class="badge rounded-pill {{ s.css }}" data-testid="status">{{ s.label }}</span>
            }
            <button
              type="button"
              class="btn btn-glass"
              title="Link teilen"
              aria-label="Link teilen"
              (click)="share()"
            >
              <app-icon name="share-2" />
            </button>
            <button
              type="button"
              class="btn btn-glass"
              title="Termin bearbeiten"
              aria-label="Termin bearbeiten"
              (click)="editOpen.set(true)"
            >
              <app-icon name="pencil" />
            </button>
          </div>
        </div>
        <div class="d-flex flex-wrap gap-3 small text-white">
          <span class="d-flex align-items-center gap-2"
            ><app-icon name="calendar" />{{ date() }}</span
          >
          <span class="d-flex align-items-center gap-2"
            ><app-icon name="clock" />{{ time() }}</span
          >
          @if (appointment().venue) {
            <span class="d-flex align-items-center gap-2"
              ><app-icon name="map-pin" />{{ appointment().venue }}</span
            >
          }
        </div>
      </div>
      <div class="card-body p-4 d-flex flex-column gap-3">
        @if (appointment().description) {
          <div>
            <h3 class="small fw-semibold text-uppercase text-body-secondary mb-2">Beschreibung</h3>
            <p class="mb-0 text-pre-wrap">{{ appointment().description }}</p>
          </div>
        }
        <div class="d-flex align-items-center gap-2 small text-body-secondary">
          <app-icon name="users" />
          <span>Mindestens {{ appointment().minimal_attendees }} Teilnehmer erforderlich</span>
        </div>
      </div>
    </div>
    <app-edit-appointment-bottom-sheet [(open)]="editOpen" [appointment]="appointment()" />
  `,
})
export class AppointmentDetailsCardComponent {
  private readonly shareService = inject(AppointmentShareService);

  readonly appointment = input.required<Appointment>();

  protected readonly editOpen = signal(false);

  protected readonly status = computed(() => STATUS[this.appointment().status]);
  protected readonly date = computed(() => formatDate(this.appointment().start));
  protected readonly time = computed(
    () => `${formatTime(this.appointment().start)} - ${formatTime(this.appointment().end)}`,
  );

  protected share(): void {
    void this.shareService.share(this.appointment());
  }
}
