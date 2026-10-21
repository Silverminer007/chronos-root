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
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(AppointmentStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('loadInitialAppointments', () => {
    it('lädt Seite 0 mit Teilnehmenden und übernimmt Liste und Paginierung', async () => {
      const result = store.loadInitialAppointments();
      expect(store.loading()).toBe(true);
      http
        .expectOne(AGENDA)
        .flush({ data: [appointment(1), appointment(2)], meta: { page: 0, size: 20, total: 2 } });
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
      http
        .expectOne(AGENDA)
        .flush({ message: 'Kaputt' }, { status: 500, statusText: 'Server Error' });

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
      http
        .expectOne(AGENDA)
        .flush({ data: [appointment(1)], meta: { page: 0, size: 1, total: 3 } });
      await result;
      expect(store.hasMore()).toBe(true);
    });

    it('loadMore hängt die nächste Seite an', async () => {
      const first = store.loadInitialAppointments();
      http
        .expectOne(AGENDA)
        .flush({ data: [appointment(1)], meta: { page: 0, size: 20, total: 21 } });
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
      http
        .expectOne(AGENDA)
        .flush({ data: [appointment(1)], meta: { page: 0, size: 20, total: 1 } });
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
      http
        .expectOne(AGENDA)
        .flush({ data: [appointment(1), appointment(2)], meta: { page: 0, size: 20, total: 2 } });
      await result;
    });

    it('approveAppointment sagt zu und aktualisiert den Termin in der Liste', async () => {
      const result = store.approveAppointment(2);
      http
        .expectOne({ method: 'POST', url: '/api/v2/appointments/2/participants/approve' })
        .flush(null);
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
      http
        .expectOne({ method: 'POST', url: '/api/v2/appointments/1/participants/reject' })
        .flush(null);
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

  describe('Termindetails', () => {
    const DETAIL = '/api/v2/appointments/3?participants=true&messages=true&group_participants=true';

    it('fetchAppointment übernimmt den Termin als aktuellen Termin', async () => {
      const result = store.fetchAppointment(3);
      http.expectOne(DETAIL).flush(appointment(3, { name: 'Detail' }));
      await result;
      expect(store.currentAppointment()?.name).toBe('Detail');
    });

    it('resetCurrentAppointment leert den aktuellen Termin und die Fehlermeldung', async () => {
      const result = store.fetchAppointment(3);
      http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Laden des Appointments');

      store.resetCurrentAppointment();
      expect(store.currentAppointment()).toBeNull();
      expect(store.error()).toBeNull();
    });

    it('lädt nach einer Mutation neu und aktualisiert den aktuellen Termin', async () => {
      const result = store.fetchAppointment(3);
      http.expectOne(DETAIL).flush(appointment(3, { name: 'Alt' }));
      await result;

      const approve = store.approveAppointment(3);
      http.expectOne('/api/v2/appointments/3/participants/approve').flush(null);
      await Promise.resolve();
      http.expectOne(DETAIL).flush(appointment(3, { name: 'Neu' }));
      await approve;

      expect(store.currentAppointment()?.name).toBe('Neu');
    });

    const mutations: {
      name: string;
      call: (s: AppointmentStore) => Promise<unknown>;
      method: string;
      url: string;
      body: unknown;
      error: string;
    }[] = [
      {
        name: 'addParticipant',
        call: (s) => s.addParticipant(3, 9, 'HELPER'),
        method: 'POST',
        url: '/api/v2/appointments/3/participants/users',
        body: { user_id: 9, user_role: 'HELPER' },
        error: 'Fehler beim Hinzufügen des Teilnehmers',
      },
      {
        name: 'removeParticipant',
        call: (s) => s.removeParticipant(3, 9),
        method: 'DELETE',
        url: '/api/v2/appointments/3/participants/users/9',
        body: null,
        error: 'Fehler beim Entfernen des Teilnehmers',
      },
      {
        name: 'changeParticipantRole',
        call: (s) => s.changeParticipantRole(3, 9, 'GUEST'),
        method: 'PATCH',
        url: '/api/v2/appointments/3/participants/users/9',
        body: { role: 'GUEST' },
        error: 'Fehler beim Ändern der Rolle',
      },
      {
        name: 'addGroupParticipant',
        call: (s) => s.addGroupParticipant(3, 4, 'ATTENDANT'),
        method: 'POST',
        url: '/api/v2/appointments/3/participants/groups',
        body: { group_id: 4, user_role: 'ATTENDANT' },
        error: 'Fehler beim Hinzufügen der Gruppe',
      },
      {
        name: 'removeGroupParticipant',
        call: (s) => s.removeGroupParticipant(3, 4),
        method: 'DELETE',
        url: '/api/v2/appointments/3/participants/groups/4',
        body: null,
        error: 'Fehler beim Entfernen der Gruppe',
      },
      {
        name: 'sendMessage',
        call: (s) => s.sendMessage(3, 'Hallo'),
        method: 'POST',
        url: '/api/v2/appointments/3/messages',
        body: { body: 'Hallo' },
        error: 'Fehler beim Senden der Nachricht',
      },
    ];

    for (const m of mutations) {
      it(`${m.name} sendet die Anfrage und lädt den Termin neu`, async () => {
        const result = m.call(store);
        const req = http.expectOne({ method: m.method, url: m.url });
        expect(req.request.body).toEqual(m.body);
        req.flush(null);
        await Promise.resolve();
        http.expectOne(DETAIL).flush(appointment(3, { name: 'Neu' }));
        await result;
        expect(store.currentAppointment()?.name).toBe('Neu');
      });

      it(`${m.name} meldet Fehler und wirft ihn weiter`, async () => {
        const result = m.call(store);
        http
          .expectOne({ method: m.method, url: m.url })
          .flush(null, { status: 500, statusText: 'Server Error' });
        await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
        expect(store.error()).toBe(m.error);
      });
    }
  });
  describe('Mutationen am Termin', () => {
    const DETAIL = '/api/v2/appointments/3?participants=true&messages=true&group_participants=true';
    const data = {
      name: 'Neu',
      description: null,
      start: '2026-10-21T18:00:00Z',
      end: '2026-10-21T19:00:00Z',
      venue: null,
      minimal_attendees: 0,
    };

    it('createAppointment sendet POST und gibt den neuen Termin zurück, ohne die Ladeanzeige zu berühren', async () => {
      const result = store.createAppointment(data);
      expect(store.loading()).toBe(false);
      const req = http.expectOne({ method: 'POST', url: '/api/v2/appointments' });
      expect(req.request.body).toEqual(data);
      req.flush(appointment(9, { name: 'Neu' }));
      expect((await result).id).toBe(9);
      expect(store.appointments().map((a) => a.id)).toEqual([9]);
    });

    it('createAppointment meldet Fehler und wirft ihn weiter', async () => {
      const result = store.createAppointment(data);
      http
        .expectOne('/api/v2/appointments')
        .flush(null, { status: 500, statusText: 'Server Error' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Erstellen des Appointments');
    });

    it('updateAppointment sendet PATCH und ersetzt den Termin in Liste und Detailansicht', async () => {
      store.appointments.set([appointment(3), appointment(4)]);
      store.currentAppointment.set(appointment(3));
      const result = store.updateAppointment(3, { name: 'Geändert' });
      const req = http.expectOne({ method: 'PATCH', url: '/api/v2/appointments/3' });
      expect(req.request.body).toEqual({ name: 'Geändert' });
      req.flush(appointment(3, { name: 'Geändert' }));
      await result;
      expect(store.appointments()[0].name).toBe('Geändert');
      expect(store.currentAppointment()?.name).toBe('Geändert');
    });

    it('updateAppointment meldet Fehler', async () => {
      const result = store.updateAppointment(3, {});
      http
        .expectOne({ method: 'PATCH', url: '/api/v2/appointments/3' })
        .flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Aktualisieren des Appointments');
    });

    it('moveAppointment ändert nur Start und Ende per PATCH', async () => {
      const result = store.moveAppointment(3, '2026-11-01T10:00:00Z', '2026-11-01T11:00:00Z');
      const req = http.expectOne({ method: 'PATCH', url: '/api/v2/appointments/3' });
      expect(req.request.body).toEqual({
        start: '2026-11-01T10:00:00Z',
        end: '2026-11-01T11:00:00Z',
      });
      req.flush(appointment(3));
      await result;
    });

    it('deleteAppointment entfernt den Termin aus Liste und Detailansicht', async () => {
      store.appointments.set([appointment(3), appointment(4)]);
      store.currentAppointment.set(appointment(3));
      const result = store.deleteAppointment(3);
      http.expectOne({ method: 'DELETE', url: '/api/v2/appointments/3' }).flush(null);
      await result;
      expect(store.appointments().map((a) => a.id)).toEqual([4]);
      expect(store.currentAppointment()).toBeNull();
    });

    it('deleteAppointment meldet Fehler und behält die Liste', async () => {
      store.appointments.set([appointment(3)]);
      const result = store.deleteAppointment(3);
      http
        .expectOne({ method: 'DELETE', url: '/api/v2/appointments/3' })
        .flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Löschen des Appointments');
      expect(store.appointments()).toHaveLength(1);
    });

    it('cancelAppointment sendet POST und lädt den Termin neu', async () => {
      const result = store.cancelAppointment(3);
      http.expectOne({ method: 'POST', url: '/api/v2/appointments/3/cancel' }).flush(null);
      await Promise.resolve();
      http.expectOne(DETAIL).flush(appointment(3, { status: 'CANCELLED' }));
      await result;
      expect(store.currentAppointment()?.status).toBe('CANCELLED');
    });

    it('cancelAppointment meldet Fehler', async () => {
      const result = store.cancelAppointment(3);
      http
        .expectOne({ method: 'POST', url: '/api/v2/appointments/3/cancel' })
        .flush(null, { status: 500, statusText: 'x' });
      await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
      expect(store.error()).toBe('Fehler beim Absagen des Appointments');
    });
  });
});
