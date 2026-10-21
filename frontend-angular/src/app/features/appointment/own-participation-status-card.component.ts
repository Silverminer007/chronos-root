import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { Appointment, ParticipationStatus } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';

/** „Deine Teilnahme“: eigene Zu-/Absage auf der Detailseite (Port von `OwnParticipationStatusCard.vue`). */
@Component({
  selector: 'app-own-participation-status-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card shadow-sm">
      <div class="card-body p-4">
        <h3 class="h5 fw-bold mb-3">Deine Teilnahme</h3>
        <div class="d-flex flex-column flex-sm-row gap-3">
          <button
            type="button"
            class="btn btn-lg flex-fill d-flex align-items-center justify-content-center gap-2"
            [class]="status() === 'APPROVED' ? 'btn-success' : 'btn-outline-success'"
            [disabled]="status() === 'APPROVED' || updating()"
            (click)="update('approve')"
          >
            <i class="bi bi-check-lg" aria-hidden="true"></i><span>Zusagen</span>
          </button>
          <button
            type="button"
            class="btn btn-lg flex-fill d-flex align-items-center justify-content-center gap-2"
            [class]="status() === 'REJECTED' ? 'btn-danger' : 'btn-outline-danger'"
            [disabled]="status() === 'REJECTED' || updating()"
            (click)="update('reject')"
          >
            <i class="bi bi-x-lg" aria-hidden="true"></i><span>Absagen</span>
          </button>
        </div>
        @if (status() === 'PENDING') {
          <div class="alert alert-warning d-flex gap-3 mt-3 mb-0" role="note">
            <i class="bi bi-exclamation-triangle" aria-hidden="true"></i>
            <div>
              <p class="fw-medium mb-1">Bitte bestätige deine Teilnahme</p>
              <p class="small mb-0">Deine Rückmeldung hilft bei der Planung des Termins.</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class OwnParticipationStatusCardComponent {
  private readonly store = inject(AppointmentStore);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();

  protected readonly updating = signal(false);
  protected readonly status = computed<ParticipationStatus>(() => {
    const userId = this.auth.user()?.id;
    return this.appointment().participants.find((p) => p.user_id === userId)?.status ?? 'PENDING';
  });

  protected async update(action: 'approve' | 'reject'): Promise<void> {
    if (this.auth.user()?.id === undefined || this.updating()) return;
    this.updating.set(true);
    try {
      const id = this.appointment().id;
      await (action === 'approve' ? this.store.approveAppointment(id) : this.store.rejectAppointment(id));
      this.toast.success(action === 'approve' ? 'Zusage bestätigt' : 'Absage registriert');
    } catch {
      this.toast.error('Fehler', 'Teilnahmestatus konnte nicht aktualisiert werden');
    } finally {
      this.updating.set(false);
    }
  }
}
