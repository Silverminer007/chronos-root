import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AppointmentNotificationSetting, NotificationSettings } from '../../core/models';
import { SettingUpdate } from './setting-update';

interface NotificationType {
  key: keyof NotificationSettings;
  label: string;
  description: string;
  icon: string;
}

interface RoleOption {
  value: AppointmentNotificationSetting;
  label: string;
  icon: string;
  tone: string;
}

/** Wahl der Rollen je Termin-Benachrichtigung (Port von `NotificationAppointmentSettingsCard.vue`). */
@Component({
  selector: 'app-notification-appointment-settings-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './notification-option.scss',
  template: `
    <div class="card shadow-sm">
      <div class="card-header p-4 d-flex align-items-center gap-3 bg-transparent">
        <span class="icon-tile flex-shrink-0" aria-hidden="true"><i class="bi bi-calendar3"></i></span>
        <div class="text-break" style="min-width: 0">
          <h3 class="h5 fw-bold mb-0">Terminbenachrichtigungen</h3>
          <p class="small text-body-secondary mb-0">Wähle für welche Rollen du benachrichtigt werden möchtest</p>
        </div>
      </div>

      <ul class="list-group list-group-flush">
        @for (type of notificationTypes; track type.key) {
          <li class="list-group-item p-4">
            <div class="d-flex align-items-start gap-3 mb-3">
              <i class="bi text-body-tertiary mt-1" [class]="type.icon" aria-hidden="true"></i>
              <div>
                <p class="fw-medium mb-0">{{ type.label }}</p>
                <p class="small text-body-secondary mb-0">{{ type.description }}</p>
              </div>
            </div>
            <div class="row row-cols-5 g-2" role="group" [attr.aria-label]="type.label">
              @for (option of roleOptions; track option.value) {
                <div class="col">
                  <button
                    type="button"
                    class="option tone-{{ option.tone }} w-100 p-2 d-flex flex-column align-items-center gap-1"
                    [class.selected]="settings()[type.key] === option.value"
                    [attr.aria-pressed]="settings()[type.key] === option.value"
                    [attr.aria-label]="option.label"
                    (click)="update.emit({ key: type.key, value: option.value })"
                  >
                    <i class="bi" [class]="option.icon" aria-hidden="true"></i>
                    <span class="small fw-medium d-none d-sm-block">{{ option.label }}</span>
                  </button>
                </div>
              }
            </div>
          </li>
        }
      </ul>
    </div>
  `,
})
export class NotificationAppointmentSettingsCardComponent {
  readonly settings = input.required<NotificationSettings>();
  readonly update = output<SettingUpdate>();

  protected readonly notificationTypes: NotificationType[] = [
    { key: 'appointment_moved', label: 'Termin verschoben', description: 'Wenn ein Termin zeitlich verschoben wird', icon: 'bi-calendar-event' },
    { key: 'appointment_message', label: 'Neue Nachricht', description: 'Wenn eine Nachricht in einem Termin gesendet wird', icon: 'bi-envelope' },
    { key: 'appointment_cancelled', label: 'Termin abgesagt', description: 'Wenn ein Termin abgesagt wird', icon: 'bi-x-circle' },
    { key: 'appointment_participant_added', label: 'Teilnehmer hinzugefügt', description: 'Wenn du zu einem Termin eingeladen wirst', icon: 'bi-person-plus' },
    { key: 'appointment_participation_status_changed', label: 'Teilnahmestatus geändert', description: 'Wenn jemand zu- oder absagt', icon: 'bi-check-circle' },
    { key: 'appointment_participation_invalid', label: 'Nicht genug Teilnehmer', description: 'Wenn ein Termin nicht genug Zusagen hat', icon: 'bi-exclamation-triangle' },
    { key: 'appointment_participation_status_pending', label: 'Rückmeldung ausstehend', description: 'Erinnerung zur Rückmeldung', icon: 'bi-clock' },
    { key: 'appointment_reminder', label: 'Terminerinnerung', description: 'Erinnerung vor dem Termin', icon: 'bi-bell' },
  ];

  protected readonly roleOptions: RoleOption[] = [
    { value: 'DISABLED', label: 'Aus', icon: 'bi-slash-circle', tone: 'secondary' },
    { value: 'ALL', label: 'Alle', icon: 'bi-people', tone: 'primary' },
    { value: 'ATTENDANT', label: 'Teiln.', icon: 'bi-person', tone: 'info' },
    { value: 'HELPER', label: 'Helfer', icon: 'bi-wrench', tone: 'success' },
    { value: 'RESPONSIBLE', label: 'Orga.', icon: 'bi-star', tone: 'warning' },
  ];
}
