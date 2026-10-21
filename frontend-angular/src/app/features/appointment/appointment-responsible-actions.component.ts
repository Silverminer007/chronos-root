import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { Appointment } from '../../core/models';
import { isResponsible } from './participation';

/**
 * Aktionen für Organisierende (Port von `AppointmentResponsibleActions.vue`). Das Absagen selbst gehört zum Ticket
 * „Termin anlegen und bearbeiten“; hier zeigt die Karte nur den Button und meldet den Klick über `cancelRequested`.
 */
@Component({
  selector: 'app-appointment-responsible-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (responsible()) {
      <div class="card shadow-sm">
        <div class="card-body p-4">
          <h3 class="h5 fw-bold mb-3">Organisator-Aktionen</h3>
          <button
            type="button"
            class="btn btn-outline-danger w-100 d-flex align-items-center justify-content-center gap-2"
            [disabled]="appointment().status === 'CANCELLED'"
            (click)="cancelRequested.emit()"
          >
            <i class="bi bi-x-circle" aria-hidden="true"></i><span>Termin absagen</span>
          </button>
        </div>
      </div>
    }
  `,
})
export class AppointmentResponsibleActionsComponent {
  private readonly auth = inject(AuthService);

  readonly appointment = input.required<Appointment>();
  readonly cancelRequested = output<void>();

  protected readonly responsible = computed(() => isResponsible(this.appointment(), this.auth.user()?.id));
}
