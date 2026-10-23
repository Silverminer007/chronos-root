import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationSettings } from '../../core/models';
import { SettingsStore } from './settings.store';

const settings = (overrides: Partial<NotificationSettings> = {}): NotificationSettings => ({
  appointment_moved: 'ALL',
  appointment_message: 'ALL',
  appointment_cancelled: 'ALL',
  appointment_participant_added: 'ALL',
  appointment_participation_status_changed: 'ALL',
  appointment_participation_invalid: 'ALL',
  appointment_participation_status_pending: 'ALL',
  appointment_reminder: 'ALL',
  group_member_added: 'ENABLED',
  ...overrides,
});

describe('SettingsStore', () => {
  let store: SettingsStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(SettingsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lädt die Einstellungen', async () => {
    const result = store.fetchSettings();
    expect(store.loading()).toBe(true);
    http.expectOne('/api/v2/settings').flush(settings({ appointment_moved: 'HELPER' }));
    await result;
    expect(store.settings()?.appointment_moved).toBe('HELPER');
    expect(store.loading()).toBe(false);
  });

  it('verwendet bei 404 die Standardwerte', async () => {
    const result = store.fetchSettings();
    http.expectOne('/api/v2/settings').flush(null, { status: 404, statusText: 'Not Found' });
    await result;
    expect(store.settings()).toEqual(settings());
    expect(store.error()).toBeNull();
  });

  it('setzt bei anderen Fehlern die Meldung und wirft weiter', async () => {
    const result = store.fetchSettings();
    http.expectOne('/api/v2/settings').flush(null, { status: 500, statusText: 'Server Error' });
    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(store.error()).toBe('Fehler beim Laden der Einstellungen');
    expect(store.settings()).toBeNull();
    expect(store.loading()).toBe(false);
  });

  it('updateSetting ändert nur den gewählten Schlüssel und ist ohne Einstellungen wirkungslos', async () => {
    store.updateSetting('appointment_moved', 'HELPER');
    expect(store.settings()).toBeNull();

    const result = store.fetchSettings();
    http.expectOne('/api/v2/settings').flush(settings());
    await result;
    store.updateSetting('appointment_moved', 'HELPER');
    expect(store.settings()).toEqual(settings({ appointment_moved: 'HELPER' }));
  });

  it('speichert per PUT und übernimmt die Antwort', async () => {
    const load = store.fetchSettings();
    http.expectOne('/api/v2/settings').flush(settings());
    await load;
    store.updateSetting('appointment_reminder', 'DISABLED');

    const result = store.saveSettings();
    expect(store.saving()).toBe(true);
    const req = http.expectOne('/api/v2/settings');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(settings({ appointment_reminder: 'DISABLED' }));
    req.flush(settings({ appointment_reminder: 'DISABLED', appointment_moved: 'RESPONSIBLE' }));
    await result;
    expect(store.settings()?.appointment_moved).toBe('RESPONSIBLE');
    expect(store.saving()).toBe(false);
  });

  it('speichert nichts ohne geladene Einstellungen', async () => {
    await store.saveSettings();
    http.expectNone('/api/v2/settings');
  });

  it('setzt beim Speicherfehler die Meldung und wirft weiter', async () => {
    const load = store.fetchSettings();
    http.expectOne('/api/v2/settings').flush(settings());
    await load;

    const result = store.saveSettings();
    http.expectOne('/api/v2/settings').flush(null, { status: 500, statusText: 'Server Error' });
    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(store.error()).toBe('Fehler beim Speichern der Einstellungen');
    expect(store.saving()).toBe(false);
  });
});
