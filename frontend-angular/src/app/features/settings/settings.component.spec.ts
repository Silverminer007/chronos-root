import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationSettings } from '../../core/models';
import { PushService } from '../../core/push.service';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { SettingsComponent } from './settings.component';

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

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  const push = {
    permission: () => 'default' as NotificationPermission,
    isPushAvailable: vi.fn(),
    isPushEnabled: vi.fn(),
    requestPermission: vi.fn(),
    subscribe: vi.fn(),
    markAsked: vi.fn(),
  };
  const el = () => fixture.nativeElement as HTMLElement;

  const create = (available = true, enabled = false) => {
    push.isPushAvailable.mockReturnValue(available);
    push.isPushEnabled.mockReturnValue(enabled);
    fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
  };

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const load = async (value = settings()) => {
    http.expectOne('/api/v2/settings').flush(value);
    await settle();
  };

  beforeEach(() => {
    Object.values(push).forEach((fn) => typeof fn === 'function' && 'mockReset' in fn && fn.mockReset());
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: PushService, useValue: push }],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => http.verify());

  it('zeigt beim Laden „Wird geladen...“', () => {
    create();
    expect(el().textContent).toContain('Wird geladen...');
    http.match(() => true);
  });

  it('zeigt die drei Karten mit den geladenen Einstellungen', async () => {
    create();
    await load();
    expect(el().querySelector('h1')?.textContent).toBe('Einstellungen');
    expect(el().querySelector('app-notification-push-card')).not.toBeNull();
    expect(el().querySelector('app-notification-appointment-settings-card')).not.toBeNull();
    expect(el().querySelector('app-notification-group-settings-card')).not.toBeNull();
  });

  it('zeigt bei einem Ladefehler die Meldung', async () => {
    create();
    http.expectOne('/api/v2/settings').flush(null, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(el().textContent).toContain('Fehler beim Laden');
    expect(el().textContent).toContain('Fehler beim Laden der Einstellungen');
  });

  it('zeigt ohne Push-Unterstützung nur den Hinweis und lädt nichts', () => {
    create(false);
    expect(el().textContent).toContain('Push Benachrichtigungen sind in deinem Browser nicht verfügbar');
    expect(buttonByText(el(), 'Speichern')).toBeUndefined();
    expect(el().querySelector('app-notification-push-card')).toBeNull();
  });

  it('übernimmt eine Auswahl und speichert alle Einstellungen', async () => {
    create();
    await load();
    click(el().querySelector('app-notification-appointment-settings-card button[aria-label="Helfer"]'));
    click(buttonByText(el(), 'Speichern'));
    const req = http.expectOne('/api/v2/settings');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.appointment_moved).toBe('HELPER');
    req.flush(settings({ appointment_moved: 'HELPER' }));
    await settle();
    expect(toast.toasts().at(-1)).toMatchObject({ severity: 'success', summary: 'Einstellungen gespeichert' });
  });

  it('meldet einen Speicherfehler', async () => {
    create();
    await load();
    click(buttonByText(el(), 'Speichern'));
    http.expectOne('/api/v2/settings').flush(null, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(toast.toasts().at(-1)).toMatchObject({ severity: 'error', summary: 'Fehler beim Speichern', detail: 'Bitte versuche es erneut' });
  });

  describe('Push-Schalter', () => {
    const toggle = async () => {
      click(el().querySelector('app-notification-push-card input'));
      await settle();
    };

    it('aktiviert Push bei erteilter Berechtigung', async () => {
      create();
      await load();
      push.requestPermission.mockResolvedValue('granted');
      await toggle();
      expect(push.subscribe).toHaveBeenCalled();
      expect(push.markAsked).toHaveBeenCalledWith('granted');
      expect(el().textContent).toContain('Aktiviert');
      expect(toast.toasts().at(-1)).toMatchObject({ severity: 'success', summary: 'Push-Benachrichtigungen aktiviert' });
    });

    it('warnt bei verweigerter Berechtigung', async () => {
      create();
      await load();
      push.requestPermission.mockResolvedValue('denied');
      await toggle();
      expect(push.subscribe).not.toHaveBeenCalled();
      expect(push.markAsked).toHaveBeenCalledWith('denied');
      expect(toast.toasts().at(-1)).toMatchObject({ severity: 'warn', summary: 'Berechtigung verweigert' });
    });

    it('meldet einen Fehler beim Abonnieren', async () => {
      create();
      await load();
      push.requestPermission.mockResolvedValue('granted');
      push.subscribe.mockRejectedValue(new Error('boom'));
      await toggle();
      expect(toast.toasts().at(-1)).toMatchObject({
        severity: 'error',
        detail: 'Push-Benachrichtigungen konnten nicht aktiviert werden',
      });
      expect(el().textContent).toContain('Deaktiviert');
    });

    it('deaktiviert Push, wenn es aktiv ist', async () => {
      create(true, true);
      await load();
      expect(el().textContent).toContain('Aktiviert');
      await toggle();
      expect(push.markAsked).toHaveBeenCalledWith('denied');
      expect(el().textContent).toContain('Deaktiviert');
      expect(toast.toasts().at(-1)).toMatchObject({ severity: 'info', summary: 'Push-Benachrichtigungen deaktiviert' });
    });
  });
});
