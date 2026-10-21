import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Appointment } from '../../core/models';
import { AppointmentStore } from './appointment.store';

const appointment = (id: number, overrides: Partial<Appointment> = {}): Appointment => ({
  id,
  name: `Termin ${id}`,
  description: null,
  start: '2026-10-21T18:00:00Z',
  end: '2026-10-21T21:00:00Z',
  venue: null,
  status: 'PLANNED',
  minimal_attendees: 0,
  participants: [],
  group_participants: [],
  messages: [],
  ...overrides,
});

const AGENDA = '/api/v2/appointments?page=0&size=20&participants=true';

describe('AppointmentStore', () => {
  let store: AppointmentStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    store = TestBed.inject(AppointmentStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('loadInitialAppointments', () => {
    it('lädt Seite 0 mit Teilnehmenden und übernimmt Liste und Paginierung', async () => {
      const result = store.loadInitialAppointments();
      expect(store.loading()).toBe(true);
      http.expectOne(AGENDA).flush({ data: [appointment(1), appointment(2)], meta: { page: 0, size: 20, total: 2 } });
      await result;

      expect(store.appointments().map((a) => a.id)).toEqual([1, 2]);
      expect(store.pagination()).toEqual({ page: 0, size: 20, total: 2 });
      expect(store.loading()).toBe(false);
      expect(store.error()).toBeNull();
    });

    it('akzeptiert auch ein reines Array als Antwort', async () => {
      const result = store.loadInitialAppointments();
      http.expectOne(AGENDA).flush([appointment(1)]);
      await result;
      expect(store.appointments().map((a) => a.id)).toEqual([1]);
    });

    it('setzt Fehlermeldung, beendet das Laden und wirft den Fehler weiter', async () => {
      const result = store.loadInitialAppointments();
      http.expectOne(AGENDA).flush({ message: 'Kaputt' }, { status: 500, statusText: 'Server Error' });

      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Kaputt');
      expect(store.loading()).toBe(false);
    });

    it('nutzt den deutschen Fallback ohne Meldung des Backends', async () => {
      const result = store.loadInitialAppointments();
      http.expectOne(AGENDA).error(new ProgressEvent('error'));
      await expect(result).rejects.toBeDefined();
      expect(store.error()).toBe('Fehler beim Laden der Appointments');
    });
  });

  describe('Paginierung', () => {
    it('hasMore ist wahr, solange nicht alle Termine geladen sind', async () => {
      const result = store.loadInitialAppointments();
      http.expectOne(AGENDA).flush({ data: [appointment(1)], meta: { page: 0, size: 1, total: 3 } });
      await result;
      expect(store.hasMore()).toBe(true);
    });

    it('loadMore hängt die nächste Seite an', async () => {
      const first = store.loadInitialAppointments();
      http.expectOne(AGENDA).flush({ data: [appointment(1)], meta: { page: 0, size: 20, total: 21 } });
      await first;

      const more = store.loadMore();
      http
        .expectOne('/api/v2/appointments?page=1&size=20&participants=true')
        .flush({ data: [appointment(2)], meta: { page: 1, size: 20, total: 21 } });
      await more;

      expect(store.appointments().map((a) => a.id)).toEqual([1, 2]);
      expect(store.pagination().page).toBe(1);
      expect(store.hasMore()).toBe(false);
    });
  });

  describe('search', () => {
    it('ersetzt die Liste durch die Treffer', async () => {
      const first = store.loadInitialAppointments();
      http.expectOne(AGENDA).flush({ data: [appointment(1)], meta: { page: 0, size: 20, total: 1 } });
      await first;

      const result = store.search('Zelt');
      http
        .expectOne('/api/v2/appointments?search=Zelt&page=0&size=20&participants=true')
        .flush({ data: [appointment(7)], meta: { page: 0, size: 20, total: 1 } });
      await result;

      expect(store.appointments().map((a) => a.id)).toEqual([7]);
    });

    it('sendet bei leerem Suchbegriff keinen search-Parameter', async () => {
      const result = store.search('');
      http.expectOne(AGENDA).flush({ data: [], meta: { page: 0, size: 20, total: 0 } });
      await result;
      expect(store.appointments()).toEqual([]);
    });

    it('behält den Suchbegriff für „Mehr laden“', async () => {
      const result = store.search('Zelt');
      http
        .expectOne('/api/v2/appointments?search=Zelt&page=0&size=20&participants=true')
        .flush({ data: [appointment(1)], meta: { page: 0, size: 20, total: 40 } });
      await result;

      const more = store.loadMore();
      http
        .expectOne('/api/v2/appointments?search=Zelt&page=1&size=20&participants=true')
        .flush({ data: [], meta: { page: 1, size: 20, total: 40 } });
      await more;
    });

    it('verwirft die späte Antwort einer überholten Suche', async () => {
      const slow = store.search('Z');
      const fast = store.search('Zelt');
      http
        .expectOne('/api/v2/appointments?search=Zelt&page=0&size=20&participants=true')
        .flush({ data: [appointment(2)], meta: { page: 0, size: 20, total: 1 } });
      http
        .expectOne('/api/v2/appointments?search=Z&page=0&size=20&participants=true')
        .flush({ data: [appointment(1)], meta: { page: 0, size: 20, total: 1 } });
      await Promise.all([slow, fast]);

      expect(store.appointments().map((a) => a.id)).toEqual([2]);
      expect(store.loading()).toBe(false);
    });
  });

  describe('Zu- und Absage', () => {
    beforeEach(async () => {
      const result = store.loadInitialAppointments();
      http.expectOne(AGENDA).flush({ data: [appointment(1), appointment(2)], meta: { page: 0, size: 20, total: 2 } });
      await result;
    });

    it('approveAppointment sagt zu und aktualisiert den Termin in der Liste', async () => {
      const result = store.approveAppointment(2);
      http.expectOne({ method: 'POST', url: '/api/v2/appointments/2/participants/approve' }).flush(null);
      await Promise.resolve();
      http
        .expectOne('/api/v2/appointments/2?participants=true&messages=true&group_participants=true')
        .flush(appointment(2, { name: 'Neu' }));
      await result;

      expect(store.appointments().map((a) => a.name)).toEqual(['Termin 1', 'Neu']);
      expect(store.loading()).toBe(false);
    });

    it('rejectAppointment sagt ab und lädt den Termin neu', async () => {
      const result = store.rejectAppointment(1);
      http.expectOne({ method: 'POST', url: '/api/v2/appointments/1/participants/reject' }).flush(null);
      await Promise.resolve();
      http
        .expectOne('/api/v2/appointments/1?participants=true&messages=true&group_participants=true')
        .flush(appointment(1, { name: 'Abgesagt' }));
      await result;
      expect(store.appointments()[0].name).toBe('Abgesagt');
    });

    it('meldet einen Fehler beim Zusagen und wirft ihn weiter', async () => {
      const result = store.approveAppointment(1);
      http
        .expectOne('/api/v2/appointments/1/participants/approve')
        .flush(null, { status: 500, statusText: 'Server Error' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Zusagen');
    });

    it('meldet einen Fehler beim Absagen und wirft ihn weiter', async () => {
      const result = store.rejectAppointment(1);
      http
        .expectOne('/api/v2/appointments/1/participants/reject')
        .flush(null, { status: 500, statusText: 'Server Error' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Absagen');
    });
  });
});
