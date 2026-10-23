import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { User } from './models';

const user: User = { id: 1, first_name: 'Max', last_name: 'Muster', email: 'max@example.org' };

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    navigateByUrl = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: { navigateByUrl } }],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('checkSession', () => {
    it('ist true bei 204 von /api/auth/isLoggedIn', async () => {
      const result = service.checkSession();
      http.expectOne('/api/auth/isLoggedIn').flush(null, { status: 204, statusText: 'No Content' });
      expect(await result).toBe(true);
    });

    it('ist false bei 401', async () => {
      const result = service.checkSession();
      http.expectOne('/api/auth/isLoggedIn').flush(null, { status: 401, statusText: 'Unauthorized' });
      expect(await result).toBe(false);
    });
  });

  describe('fetchUser', () => {
    it('lädt den Benutzer in das Signal', async () => {
      const result = service.fetchUser();
      http.expectOne('/api/v2/user').flush(user);
      await result;
      expect(service.user()).toEqual(user);
    });

    it('setzt das Signal bei Fehler auf null', async () => {
      service.user.set(user);
      const result = service.fetchUser();
      http.expectOne('/api/v2/user').flush(null, { status: 500, statusText: 'Error' });
      await result;
      expect(service.user()).toBeNull();
    });
  });

  describe('Profilprüfung', () => {
    it('ist unvollständig ohne Benutzer', () => {
      expect(service.isProfileComplete()).toBe(false);
    });

    it('ist unvollständig, wenn Vor- oder Nachname fehlt', () => {
      service.user.set({ ...user, first_name: null });
      expect(service.isProfileComplete()).toBe(false);
      service.user.set({ ...user, last_name: '  ' });
      expect(service.isProfileComplete()).toBe(false);
    });

    it('ist vollständig mit Vor- und Nachname', () => {
      service.user.set(user);
      expect(service.isProfileComplete()).toBe(true);
    });
  });

  it('updateProfile übernimmt den Benutzer und gibt die Verifizierungs-URL zurück', async () => {
    const payload = { first_name: 'Max', last_name: 'Muster', email: 'max@example.org' };
    const result = service.updateProfile(payload);
    const req = http.expectOne((r) => r.url === '/api/v2/user' && r.method === 'PATCH');
    expect(req.request.params.get('redirectUri')).toBe(window.location.origin + window.location.pathname);
    expect(req.request.body).toEqual(payload);
    req.flush({ user, verifyEmailUrl: 'https://kc/verify' });
    expect(await result).toBe('https://kc/verify');
    expect(service.user()).toEqual(user);
  });

  it('lädt und löscht verknüpfte Konten', async () => {
    const list = service.fetchLinkedAccounts();
    http.expectOne('/api/v2/user/linked').flush([{ provider: 'google' }]);
    expect(await list).toEqual([{ provider: 'google' }]);

    const del = service.unlinkAccount('google');
    const req = http.expectOne('/api/v2/user/linked/google');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await del;
  });

  it('lädt und löscht Passkeys', async () => {
    const list = service.fetchPasskeys();
    http.expectOne('/api/v2/user/passkeys').flush([]);
    expect(await list).toEqual([]);
  });

  it('logout ruft POST /api/auth/logout, leert den Benutzer und navigiert auf /', async () => {
    service.user.set(user);
    const redirect = vi.spyOn(service, 'redirectTo').mockImplementation(() => undefined);
    const result = service.logout();
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await result;
    expect(service.user()).toBeNull();
    expect(redirect).toHaveBeenCalledWith('/');
  });

  it('logout navigiert auch bei Fehler auf /', async () => {
    const redirect = vi.spyOn(service, 'redirectTo').mockImplementation(() => undefined);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const result = service.logout();
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
    await result;
    expect(redirect).toHaveBeenCalledWith('/');
  });

  describe('Sitzungsende', () => {
    it('navigiert bei SESSION_EXPIRED vom Service Worker auf /', () => {
      const target = new EventTarget();
      service.listenForSessionExpiry(target as unknown as ServiceWorkerContainer);
      target.dispatchEvent(new MessageEvent('message', { data: { type: 'SESSION_EXPIRED' } }));
      expect(navigateByUrl).toHaveBeenCalledWith('/');
    });

    it('ignoriert andere Nachrichten', () => {
      const target = new EventTarget();
      service.listenForSessionExpiry(target as unknown as ServiceWorkerContainer);
      target.dispatchEvent(new MessageEvent('message', { data: { type: 'ANDERES' } }));
      expect(navigateByUrl).not.toHaveBeenCalled();
    });
  });
});
