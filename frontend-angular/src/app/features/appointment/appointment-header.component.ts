import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { BackNavigation } from './back-navigation';
import { Appointment } from '../../core/models';

/** Kopfzeile der Detailseite mit Zurück-Button (Port von `AppointmentHeader.vue`). */
@Component({
  selector: 'app-appointment-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="sticky-top bg-body border-bottom">
      <div class="container py-3 d-flex align-items-center gap-3">
        <button type="button" class="btn btn-outline-secondary border-0 flex-shrink-0" aria-label="Zurück" (click)="backNavigation.back()">
          <i class="bi bi-arrow-left" aria-hidden="true"></i>
        </button>
        <h1 class="h4 fw-bold mb-0 text-truncate">{{ appointment().name }}</h1>
      </div>
    </header>
  `,
})
export class AppointmentHeaderComponent {
  protected readonly backNavigation = inject(BackNavigation);

  readonly appointment = input.required<Appointment>();
}
