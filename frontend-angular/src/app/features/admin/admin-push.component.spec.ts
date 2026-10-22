import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { buttonByText, click } from '../appointment/testing';
import { AdminPushComponent, parsePayload } from './admin-push.component';
import { PushLogEntry } from './admin.models';

const entry = (id: number, overrides: Partial<PushLogEntry> = {}): PushLogEntry => ({
  id,
  user_id: id,
  notification_type: 'appointment',
  payload: JSON.stringify({ title: `Titel ${id}`, body: `Text ${id}` }),
  status_code: 201,
  success: true,
  error_message: null,
  created_at: '2026-10-20T12:00:00',
  ...overrides,
});

describe('parsePayload', () => {
  it('liest Titel und Text', () => expect(parsePayload('{"title":"T","body":"B"}')).toEqual({ title: 'T', body: 'B' }));
  it('ergänzt fehlende Felder', () => expect(parsePayload('{}')).toEqual({ title: '', body: '' }));
  it('nimmt ungültiges JSON als Text', () => expect(parsePayload('roh')).toEqual({ title: '', body: 'roh' }));
});

describe('AdminPushComponent', () => {
  let fixture: ComponentFixture<AdminPushComponent>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user: signal(null) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminPushComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const load = async (entries: PushLogEntry[]) => {
    http.expectOne('/api/v2/admin/push/log').flush(entries);
    await settle();
  };

  const rows = () => Array.from(el().querySelectorAll('.log-entry'));
  const input = (label: string) =>
    Array.from(el().querySelectorAll('label'))
      .find((l) => l.textContent?.trim() === label)!
      .parentElement!.querySelector('input')!;
  const type = (field: HTMLInputElement, value: string) => {
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  it('zeigt „Wird geladen...“ und den Zurück-Link', () => {
    expect(el().textContent).toContain('Wird geladen...');
    expect(el().querySelector('a[href="/admin"]')?.textContent).toContain('Zurück');
    http.expectOne('/api/v2/admin/push/log');
  });

  it('listet Einträge mit Titel, Nutzer, Typ, Statuscode und Text', async () => {
    await load([entry(1), entry(2, { success: false, status_code: 410, error_message: 'Gone', payload: 'roh', notification_type: null })]);
    expect(rows()).toHaveLength(2);
    expect(rows()[0].textContent).toContain('Titel 1');
    expect(rows()[0].textContent).toContain('User 1');
    expect(rows()[0].textContent).toContain('appointment');
    expect(rows()[0].textContent).toContain('201');
    expect(rows()[0].textContent).toContain('Text 1');
    expect(rows()[1].textContent).toContain('Ohne Titel');
    expect(rows()[1].textContent).toContain('—');
    expect(rows()[1].textContent).toContain('410');
    expect(rows()[1].textContent).toContain('Gone');
    expect(rows()[1].textContent).toContain('roh');
  });

  it('zeigt den Leerzustand', async () => {
    await load([]);
    expect(el().textContent).toContain('Keine Einträge');
    expect(el().textContent).toContain('Es wurden keine Push-Benachrichtigungen gefunden.');
  });

  it('zeigt Ladefehler', async () => {
    http.expectOne('/api/v2/admin/push/log').flush(null, { status: 403, statusText: 'Forbidden' });
    await settle();
    expect(el().querySelector('[role=alert]')?.textContent).toContain('Fehler beim Laden');
  });

  it('filtert nach User-ID und Typ', async () => {
    await load([entry(1), entry(2, { notification_type: 'group' }), entry(3)]);
    type(input('User ID'), '2');
    expect(rows()).toHaveLength(1);
    type(input('User ID'), '');
    type(input('Benachrichtigungstyp'), 'appointment');
    expect(rows()).toHaveLength(2);
  });

  it('filtert nach Status', async () => {
    await load([entry(1), entry(2, { success: false })]);
    click(buttonByText(el(), 'Erfolgreich'));
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(rows()[0].textContent).toContain('Titel 1');
    click(buttonByText(el(), 'Fehlgeschlagen'));
    fixture.detectChanges();
    expect(rows()[0].textContent).toContain('Titel 2');
    click(buttonByText(el(), 'Alle'));
    fixture.detectChanges();
    expect(rows()).toHaveLength(2);
  });

  it('filtert nach Zeitraum inklusive Start- und Endtag', async () => {
    await load([
      entry(1, { created_at: '2026-10-18T23:59:00' }),
      entry(2, { created_at: '2026-10-19T00:00:00' }),
      entry(3, { created_at: '2026-10-20T23:59:59' }),
      entry(4, { created_at: '2026-10-21T00:00:00' }),
    ]);
    type(input('Von'), '2026-10-19');
    type(input('Bis'), '2026-10-20');
    expect(rows().map((r) => r.textContent?.match(/Titel \d/)?.[0])).toEqual(['Titel 2', 'Titel 3']);
    expect(input('Bis').min).toBe('2026-10-19');
  });
});
