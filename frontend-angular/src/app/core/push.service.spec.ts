import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PushService } from './push.service';

const DAY_MS = 24 * 60 * 60 * 1000;

describe('PushService', () => {
  let service: PushService;
  let http: HttpTestingController;
  let notification: { permission: NotificationPermission; requestPermission: ReturnType<typeof vi.fn> };
  let pushManager: { subscribe: ReturnType<typeof vi.fn>; getSubscription: ReturnType<typeof vi.fn> };

  const subscription = (endpoint = 'https://push/e1') => ({
    endpoint,
    toJSON: () => ({ keys: { p256dh: 'p256', auth: 'au' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  });

  const create = (permission: NotificationPermission = 'default') => {
    notification.permission = permission;
    service = TestBed.inject(PushService);
  };

  beforeEach(() => {
    localStorage.clear();
    notification = { permission: 'default', requestPermission: vi.fn() };
    pushManager = { subscribe: vi.fn(), getSubscription: vi.fn() };
    vi.stubGlobal('Notification', notification);
    vi.stubGlobal('PushManager', class {});
    vi.stubGlobal('navigator', {
      ...navigator,
      serviceWorker: { ready: Promise.resolve({ pushManager }) },
    });
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  describe('Verfügbarkeit', () => {
    it('ist verfügbar mit Service Worker, PushManager und Notification', () => {
      create();
      expect(service.isPushAvailable()).toBe(true);
    });

    it('ist ohne Notification nicht verfügbar und verweigert die Berechtigung', async () => {
      vi.stubGlobal('Notification', undefined);
      service = TestBed.inject(PushService);
      expect(service.isPushAvailable()).toBe(false);
      expect(await service.requestPermission()).toBe('denied');
      expect(await service.subscribe()).toBeNull();
    });
  });

  describe('permission / requestPermission', () => {
    it('übernimmt die Browser-Berechtigung beim Start', () => {
      create('denied');
      expect(service.permission()).toBe('denied');
    });

    it('aktualisiert das Signal mit dem Ergebnis der Abfrage', async () => {
      create();
      notification.requestPermission.mockResolvedValue('granted');
      expect(await service.requestPermission()).toBe('granted');
      expect(service.permission()).toBe('granted');
    });
  });

  describe('isPushEnabled', () => {
    it('braucht Browser-Berechtigung und gespeicherte Zusage', () => {
      create('granted');
      expect(service.isPushEnabled()).toBe(false);
      localStorage.setItem('pushPromptAnswer', 'granted');
      expect(service.isPushEnabled()).toBe(true);
      notification.permission = 'denied';
      expect(service.isPushEnabled()).toBe(false);
    });
  });

  describe('subscribe', () => {
    it('holt den Schlüssel, abonniert und meldet das Abo am Backend an', async () => {
      create('granted');
      const sub = subscription();
      pushManager.subscribe.mockResolvedValue(sub);

      const result = service.subscribe();
      await vi.waitFor(() => http.expectOne('/api/v2/push/public-key').flush('AQID'));
      const post = await vi.waitFor(() => http.expectOne('/api/v2/push/subscribe'));
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({ endpoint: 'https://push/e1', keys: { p256dh: 'p256', auth: 'au' } });
      post.flush(null);

      expect(await result).toBe(sub);
      const options = pushManager.subscribe.mock.calls[0][0];
      expect(options.userVisibleOnly).toBe(true);
      expect(Array.from(options.applicationServerKey)).toEqual([1, 2, 3]);
    });
  });

  describe('markAsked', () => {
    it('speichert Zeitpunkt und erste Antwort', () => {
      create();
      service.markAsked('granted');
      expect(localStorage.getItem('pushPromptShownAt')).toMatch(/^\d+$/);
      expect(localStorage.getItem('pushPromptAnswer')).toBe('granted');
    });

    it('überschreibt eine endgültige Antwort nicht, wohl aber „dismissed“', () => {
      create();
      service.markAsked('dismissed');
      service.markAsked('denied');
      expect(localStorage.getItem('pushPromptAnswer')).toBe('denied');
      service.markAsked('granted');
      expect(localStorage.getItem('pushPromptAnswer')).toBe('denied');
    });
  });

  describe('shouldAsk', () => {
    it('fragt nicht, wenn Push nicht verfügbar ist', async () => {
      vi.stubGlobal('Notification', undefined);
      service = TestBed.inject(PushService);
      expect(await service.shouldAsk()).toBe(false);
    });

    it('fragt nicht, wenn der Browser blockiert oder der Nutzer abgelehnt hat', async () => {
      create('denied');
      expect(await service.shouldAsk()).toBe(false);
      notification.permission = 'default';
      localStorage.setItem('pushPromptAnswer', 'denied');
      expect(await service.shouldAsk()).toBe(false);
    });

    it('fragt auf einem neuen Gerät', async () => {
      create();
      expect(await service.shouldAsk()).toBe(true);
    });

    it('fragt nach 7 Tagen erneut, vorher nicht', async () => {
      create();
      localStorage.setItem('pushPromptShownAt', String(Date.now() - 6 * DAY_MS));
      expect(await service.shouldAsk()).toBe(false);
      localStorage.setItem('pushPromptShownAt', String(Date.now() - 8 * DAY_MS));
      expect(await service.shouldAsk()).toBe(true);
    });

    it('fragt erneut, wenn der Nutzer zugesagt hat, der Browser aber nicht mehr „granted“ meldet', async () => {
      create();
      localStorage.setItem('pushPromptAnswer', 'granted');
      expect(await service.shouldAsk()).toBe(true);
    });

    it('abonniert still, wenn zugesagt, berechtigt, aber kein Abo vorhanden ist', async () => {
      create('granted');
      localStorage.setItem('pushPromptAnswer', 'granted');
      pushManager.getSubscription.mockResolvedValue(null);
      pushManager.subscribe.mockResolvedValue(subscription());

      const result = service.shouldAsk();
      await vi.waitFor(() => http.expectOne('/api/v2/push/public-key').flush('AQID'));
      await vi.waitFor(() => http.expectOne('/api/v2/push/subscribe').flush(null));
      expect(await result).toBe(false);
    });

    it('belässt ein vom Server bestätigtes Abo', async () => {
      create('granted');
      localStorage.setItem('pushPromptAnswer', 'granted');
      pushManager.getSubscription.mockResolvedValue(subscription('https://push/e2'));

      const result = service.shouldAsk();
      const status = await vi.waitFor(() => http.expectOne((r) => r.url === '/api/v2/push/status'));
      expect(status.request.params.get('endpoint')).toBe('https://push/e2');
      status.flush({ exists: true });
      expect(await result).toBe(false);
      expect(pushManager.subscribe).not.toHaveBeenCalled();
    });

    it('meldet ein dem Server unbekanntes Abo neu an', async () => {
      create('granted');
      localStorage.setItem('pushPromptAnswer', 'granted');
      const stale = subscription('https://push/old');
      pushManager.getSubscription.mockResolvedValue(stale);
      pushManager.subscribe.mockResolvedValue(subscription('https://push/new'));
      vi.spyOn(console, 'warn').mockImplementation(() => undefined);

      const result = service.shouldAsk();
      (await vi.waitFor(() => http.expectOne((r) => r.url === '/api/v2/push/status'))).flush({ exists: false });
      await vi.waitFor(() => http.expectOne('/api/v2/push/public-key').flush('AQID'));
      const post = await vi.waitFor(() => http.expectOne('/api/v2/push/subscribe'));
      expect(post.request.body.endpoint).toBe('https://push/new');
      post.flush(null);
      expect(await result).toBe(false);
      expect(stale.unsubscribe).toHaveBeenCalled();
    });
  });
});
