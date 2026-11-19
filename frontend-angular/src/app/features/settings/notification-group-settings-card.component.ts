import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { GroupNotificationSetting, NotificationSettings } from '../../core/models';
import { SettingUpdate } from './setting-update';
import { IconComponent } from '../../shared/icon/icon.component';

interface GroupOption {
  value: GroupNotificationSetting;
  label: string;
  icon: string;
  tone: string;
}

/** Ein/Aus für Gruppen-Benachrichtigungen (Port von `NotificationGroupSettingsCard.vue`). */
@Component({
  imports: [IconComponent],
  selector: 'app-notification-group-settings-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './notification-option.scss',
  template: `
    <div class="card shadow-sm">
      <div class="card-header p-3 p-sm-4 d-flex align-items-center gap-3 bg-transparent">
        <span class="icon-tile d-none d-sm-inline-flex flex-shrink-0" aria-hidden="true"><app-icon name="network" /></span>
        <div class="text-break" style="min-width: 0">
          <h3 class="card-heading fw-bold mb-0">Gruppenbenachrichtigungen</h3>
          <p class="small text-body-secondary mb-0">Benachrichtigungen für Gruppenaktivitäten</p>
        </div>
      </div>

      <div class="card-body p-4">
        <div class="d-flex align-items-center justify-content-between gap-3">
          <div class="d-flex align-items-start gap-3">
            <app-icon name="user-plus" class="text-body-tertiary mt-1" />
            <div>
              <p class="fw-semibold mb-0">Mitglied hinzugefügt</p>
              <p class="small text-body-secondary mb-0">Wenn ein neues Mitglied einer Gruppe beitritt</p>
            </div>
          </div>
          <div class="d-flex gap-2 flex-shrink-0" role="group" aria-label="Mitglied hinzugefügt">
            @for (option of options; track option.value) {
              <button
                type="button"
                class="option tone-{{ option.tone }} px-3 py-2 d-flex align-items-center gap-2"
                [class.selected]="settings().group_member_added === option.value"
                [attr.aria-pressed]="settings().group_member_added === option.value"
                (click)="update.emit({ key: 'group_member_added', value: option.value })"
              >
                <app-icon [name]="option.icon" />
                <span class="small fw-medium">{{ option.label }}</span>
              </button>
            }
          </div>
        </div>
      </div>
    </div>
  `,
})
export class NotificationGroupSettingsCardComponent {
  readonly settings = input.required<NotificationSettings>();
  readonly update = output<SettingUpdate>();

  protected readonly options: GroupOption[] = [
    { value: 'DISABLED', label: 'Aus', icon: 'ban', tone: 'secondary' },
    { value: 'ENABLED', label: 'An', icon: 'check', tone: 'success' },
  ];
}
