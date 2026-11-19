import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from '../../shared/icon/icon.component';

/** Schalter für Push-Benachrichtigungen auf diesem Gerät (Port von `NotificationPushCard.vue`). */
@Component({
  imports: [IconComponent],
  selector: 'app-notification-push-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card shadow-sm">
      <div class="card-body p-3 p-sm-4">
        <div class="d-flex align-items-center gap-3">
          <span class="icon-tile flex-shrink-0" aria-hidden="true"><app-icon name="bell" /></span>
          <div class="flex-grow-1 text-break" style="min-width: 0">
            <h3 class="card-heading fw-bold mb-0">Push-Benachrichtigungen</h3>
            <p class="small text-body-secondary mb-0">Erhalte Benachrichtigungen auf diesem Gerät</p>
            <p class="small mb-0 mt-1" [class]="statusClass()">{{ statusText() }}</p>
          </div>
          <div class="form-check form-switch fs-4 mb-0 ps-0 flex-shrink-0">
            <input
              class="form-check-input float-none ms-0 mt-0"
              type="checkbox"
              role="switch"
              aria-label="Push-Benachrichtigungen"
              [checked]="enabled()"
              [disabled]="permission() === 'denied'"
              (change)="toggled.emit()"
            />
          </div>
        </div>

        @if (permission() === 'denied') {
          <div class="alert alert-warning mt-3 mb-0" role="alert">
            <app-icon name="info" class="me-2" />
            Push-Benachrichtigungen wurden im Browser blockiert. Bitte ändere die Berechtigung in den Browsereinstellungen.
          </div>
        }
      </div>
    </div>
  `,
})
export class NotificationPushCardComponent {
  readonly enabled = input.required<boolean>();
  readonly permission = input.required<NotificationPermission>();
  readonly toggled = output<void>();

  protected readonly statusText = computed(() => {
    if (this.permission() === 'denied') return 'Im Browser blockiert';
    return this.enabled() ? 'Aktiviert' : 'Deaktiviert';
  });

  protected readonly statusClass = computed(() => {
    if (this.permission() === 'denied') return 'text-danger';
    return this.enabled() ? 'text-success' : 'text-body-secondary';
  });
}
