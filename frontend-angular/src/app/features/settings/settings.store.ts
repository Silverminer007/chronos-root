import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { getErrorMessage, getErrorStatus } from '../../core/errors';
import { NotificationSettings } from '../../core/models';

const API = '/api/v2/settings';

const DEFAULT_SETTINGS: NotificationSettings = {
  appointment_moved: 'ALL',
  appointment_message: 'ALL',
  appointment_cancelled: 'ALL',
  appointment_participant_added: 'ALL',
  appointment_participation_status_changed: 'ALL',
  appointment_participation_invalid: 'ALL',
  appointment_participation_status_pending: 'ALL',
  appointment_reminder: 'ALL',
  group_member_added: 'ENABLED',
};

/** Benachrichtigungseinstellungen (Port von `stores/settings.ts`); Zustand als Signals. */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly http = inject(HttpClient);

  readonly settings = signal<NotificationSettings | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  /** Ein 404 heißt „noch nie gespeichert“ und ergibt die Standardwerte. */
  async fetchSettings(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.settings.set(await firstValueFrom(this.http.get<NotificationSettings>(API)));
    } catch (err) {
      if (getErrorStatus(err) === 404) {
        this.settings.set({ ...DEFAULT_SETTINGS });
      } else {
        this.error.set(getErrorMessage(err, 'Fehler beim Laden der Einstellungen'));
        throw err;
      }
    } finally {
      this.loading.set(false);
    }
  }

  async saveSettings(): Promise<void> {
    const current = this.settings();
    if (!current) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      this.settings.set(await firstValueFrom(this.http.put<NotificationSettings>(API, current)));
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Speichern der Einstellungen'));
      throw err;
    } finally {
      this.saving.set(false);
    }
  }

  updateSetting(key: keyof NotificationSettings, value: string): void {
    this.settings.update((current) => (current ? { ...current, [key]: value } : current));
  }
}
