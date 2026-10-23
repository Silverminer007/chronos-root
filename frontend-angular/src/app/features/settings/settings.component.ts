import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { PushService } from '../../core/push.service';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';
import { NotificationAppointmentSettingsCardComponent } from './notification-appointment-settings-card.component';
import { NotificationGroupSettingsCardComponent } from './notification-group-settings-card.component';
import { NotificationPushCardComponent } from './notification-push-card.component';
import { SettingUpdate } from './setting-update';
import { SettingsStore } from './settings.store';

/** Seite `/settings` mit den Benachrichtigungseinstellungen (Port von `pages/settings.vue`). */
@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SearchHeaderComponent,
    NotificationPushCardComponent,
    NotificationAppointmentSettingsCardComponent,
    NotificationGroupSettingsCardComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  protected readonly store = inject(SettingsStore);
  protected readonly push = inject(PushService);
  private readonly toast = inject(ToastService);

  protected readonly pushAvailable = this.push.isPushAvailable();
  protected readonly pushEnabled = signal(this.push.isPushEnabled());

  ngOnInit(): void {
    // Ohne Push gibt es nichts einzustellen; die Seite zeigt dann nur den Hinweis.
    if (this.pushAvailable) {
      // Fehler setzt der Store als Meldung, die Seite zeigt sie an.
      this.store.fetchSettings().catch(() => undefined);
    }
  }

  protected async togglePush(): Promise<void> {
    if (this.pushEnabled()) {
      this.push.markAsked('denied');
      this.pushEnabled.set(false);
      this.toast.add({ severity: 'info', summary: 'Push-Benachrichtigungen deaktiviert', life: 3000 });
      return;
    }
    try {
      if ((await this.push.requestPermission()) === 'granted') {
        await this.push.subscribe();
        this.push.markAsked('granted');
        this.pushEnabled.set(true);
        this.toast.add({ severity: 'success', summary: 'Push-Benachrichtigungen aktiviert', life: 3000 });
      } else {
        this.push.markAsked('denied');
        this.toast.add({
          severity: 'warn',
          summary: 'Berechtigung verweigert',
          detail: 'Du kannst die Berechtigung in den Browsereinstellungen ändern.',
          life: 5000,
        });
      }
    } catch {
      this.toast.add({
        severity: 'error',
        summary: 'Fehler',
        detail: 'Push-Benachrichtigungen konnten nicht aktiviert werden',
        life: 3000,
      });
    }
  }

  protected updateSetting({ key, value }: SettingUpdate): void {
    this.store.updateSetting(key, value);
  }

  protected async save(): Promise<void> {
    try {
      await this.store.saveSettings();
      this.toast.add({ severity: 'success', summary: 'Einstellungen gespeichert', life: 3000 });
    } catch {
      this.toast.add({ severity: 'error', summary: 'Fehler beim Speichern', detail: 'Bitte versuche es erneut', life: 3000 });
    }
  }
}
