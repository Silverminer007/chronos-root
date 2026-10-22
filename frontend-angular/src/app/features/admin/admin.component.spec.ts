import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ModalService } from '../../core/modal.service';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { AdminComponent } from './admin.component';
import { AdminStatistics, AdminUser } from './admin.models';

const statistics: AdminStatistics = {
  users: { total: 12 },
  appointments: { total: 20, planned: 7, cancelled: 1, deleted: 2, notEnoughAttendees: 0 },
  participations: { total: 50, pending: 5, approved: 40, rejected: 5 },
  groups: { total: 4, totalMembers: 9 },
  friendships: { total: 10, pending: 2, accepted: 8, declined: 0 },
  messages: { total: 33 },
  pushSubscriptions: { total: 6 },
};

const user = (id: number, last_seen = new Date().toISOString()): AdminUser => ({
  id,
  firstName: `Vor${id}`,
  lastName: `Nach${id}`,
  email: `u${id}@example.org`,
  createdAt: last_seen,
  lastUpdate: last_seen,
  lastSeen: last_seen,
});

describe('AdminComponent', () => {
  let fixture: ComponentFixture<AdminComponent>;
  let http: HttpTestingController;
  let toast: ToastService;
  const modal = { confirm: vi.fn() };
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    modal.confirm.mockReset().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ModalService, useValue: modal },
        { provide: AuthService, useValue: { user: signal(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const load = async (users: AdminUser[] = []) => {
    http.expectOne('/api/v2/admin/statistics').flush(statistics);
    const req = http.expectOne((r) => r.url === '/api/v2/admin/user/');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ items: users, page: 0, size: 20, total: users.length });
    await settle();
  };

  it('zeigt die sechs Kennzahlen', async () => {
    await load();
    const text = el().textContent ?? '';
    for (const part of ['Administration', 'Registrierte Nutzer', 'Anstehende Termine', 'Gruppen', 'Freundschaften', 'Nachrichten', 'Push-Abonnements']) {
      expect(text).toContain(part);
    }
    const values = Array.from(el().querySelectorAll('.stat-value')).map((v) => v.textContent?.trim());
    expect(values).toEqual(['12', '7', '4', '8', '33', '6']);
  });

  it('verlinkt nur die Push-Kachel auf /admin/push', async () => {
    await load();
    const links = Array.from(el().querySelectorAll('a.stat-card')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/admin/push']);
  });

  it('zeigt Nullen, solange keine Statistik vorliegt', async () => {
    http.expectOne('/api/v2/admin/statistics').flush(null, { status: 403, statusText: 'Forbidden' });
    http.expectOne((r) => r.url === '/api/v2/admin/user/').flush(null, { status: 403, statusText: 'Forbidden' });
    await settle();
    expect(Array.from(el().querySelectorAll('.stat-value')).map((v) => v.textContent?.trim())).toEqual(['0', '0', '0', '0', '0', '0']);
    expect(el().querySelector('[role=alert]')?.textContent).toContain('Fehler beim Laden');
  });

  it('listet die letzten Anmeldungen mit Initialen', async () => {
    await load([user(1), user(2)]);
    const items = el().querySelectorAll('.recent-logins li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Vor1 Nach1');
    expect(items[0].textContent).toContain('VN');
    expect(items[0].textContent).toMatch(/vor \d+ Sekunden/);
  });

  it('verteilt die Nutzer per id % 3 auf die A/B-Varianten', async () => {
    await load([user(3), user(4), user(5), user(6)]);
    const cols = Array.from(el().querySelectorAll('.ab-variant'));
    expect(cols.map((c) => c.querySelector('.badge')?.textContent?.trim())).toEqual(['2', '1', '1']);
    expect(cols[0].textContent).toContain('Variante a');
    expect(cols[0].textContent).toContain('Vor3 Nach3');
    expect(cols[0].textContent).toContain('Vor6 Nach6');
    expect(cols[1].textContent).toContain('Vor4 Nach4');
    expect(cols[2].textContent).toContain('Vor5 Nach5');
  });

  it('zeigt „Keine Nutzer“ für leere Varianten', async () => {
    await load([]);
    expect(el().querySelectorAll('.ab-variant')[0].textContent).toContain('Keine Nutzer');
  });

  it('sendet die Umfrage-Einladungen nach Bestätigung', async () => {
    await load();
    click(buttonByText(el(), 'Umfrage-Einladungen senden'));
    expect(modal.confirm).toHaveBeenCalledWith({
      title: 'Umfrage-Einladungen senden',
      message: 'Es wird eine Push-Benachrichtigung an alle Nutzer gesendet, die noch nicht eingeladen wurden. Fortfahren?',
      confirmText: 'Senden',
      confirmColor: 'purple',
    });
    await settle();
    const req = http.expectOne('/api/v2/admin/survey/11111111-1111-1111-1111-111111111111/notify');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    await settle();
    expect(toast.toasts()[0]).toMatchObject({ severity: 'success', summary: 'Einladungen versendet' });
  });

  it('sendet nichts, wenn die Bestätigung abgebrochen wird', async () => {
    modal.confirm.mockResolvedValue(false);
    await load();
    click(buttonByText(el(), 'Umfrage-Einladungen senden'));
    await settle();
    http.expectNone((r) => r.url.includes('/notify'));
  });

  it('meldet Fehler beim Versenden', async () => {
    await load();
    click(buttonByText(el(), 'Umfrage-Einladungen senden'));
    await settle();
    http.expectOne((r) => r.url.includes('/notify')).flush(null, { status: 500, statusText: 'x' });
    await settle();
    expect(toast.toasts()[0]).toMatchObject({ severity: 'error', summary: 'Fehler beim Versenden', detail: 'Bitte versuche es erneut.' });
    expect(buttonByText(el(), 'Umfrage-Einladungen senden')?.disabled).toBe(false);
  });
});
