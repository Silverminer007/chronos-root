import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { errorInterceptor, GLOBAL_ERROR_TOAST } from './error.interceptor';
import { ToastService } from './toast.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let toast: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => ctrl.verify());

  const optIn = () => new HttpContext().set(GLOBAL_ERROR_TOAST, true);

  const fail = (url: string, status: number, body: object | null = null, context?: HttpContext) => {
    let error: unknown;
    http.get(url, { context }).subscribe({ error: (e) => (error = e) });
    ctrl.expectOne(url).flush(body, { status, statusText: 'x' });
    return error;
  };

  it('reicht den ursprünglichen Fehler an den Aufrufer weiter', () => {
    expect(fail('/api/v2/appointments', 500)).toMatchObject({ status: 500 });
  });

  it('zeigt standardmäßig keinen Toast, damit Aufrufer mit eigener Fehlerbehandlung keinen zweiten erhalten', () => {
    fail('/api/v2/appointments', 500);
    fail('/api/v2/appointments', 0);
    fail('/api/v2/appointments/1', 403);
    expect(toast.toasts()).toHaveLength(0);
  });

  it('zeigt bei Serverfehlern auf Anforderung einen deutschen Toast', () => {
    fail('/api/v2/appointments', 500, null, optIn());
    expect(toast.toasts()).toEqual([
      expect.objectContaining({ severity: 'error', detail: 'Der Server ist derzeit nicht erreichbar oder hat einen Fehler gemeldet.' }),
    ]);
  });

  it('zeigt bei Verbindungsabbruch (Status 0) auf Anforderung einen deutschen Toast', () => {
    fail('/api/v2/appointments', 0, null, optIn());
    expect(toast.toasts()[0].detail).toBe('Keine Verbindung zum Server. Bitte prüfe deine Internetverbindung.');
  });

  it('zeigt bei 403 auf Anforderung einen Toast', () => {
    fail('/api/v2/appointments/1', 403, null, optIn());
    expect(toast.toasts()[0].detail).toBe('Dazu hast du keine Berechtigung.');
  });

  it('zeigt bei 4xx wie 400/404 keinen Toast (Aufrufer behandeln diese Fälle)', () => {
    fail('/api/v2/appointments/1', 404);
    fail('/api/v2/appointments', 400, { message: 'Name fehlt' });
    expect(toast.toasts()).toHaveLength(0);
  });

  it('zeigt bei 401 keinen Toast (Sitzungsende regelt der Service Worker)', () => {
    fail('/api/v2/appointments', 401, null, optIn());
    expect(toast.toasts()).toHaveLength(0);
  });

  it('zeigt für /api/auth/isLoggedIn keinen Toast', () => {
    fail('/api/auth/isLoggedIn', 500, null, optIn());
    expect(toast.toasts()).toHaveLength(0);
  });
});
