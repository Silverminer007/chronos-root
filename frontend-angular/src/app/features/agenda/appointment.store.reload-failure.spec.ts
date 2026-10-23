import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { Appointment } from '../../core/models';
import { AppointmentStore } from './appointment.store';

const DETAIL = '/api/v2/appointments/351?participants=true&messages=true&group_participants=true';

/**
 * Issue #246: Gelingt die Aktion (POST 200), scheitert aber das anschließende Neuladen (GET 404),
 * darf die Aktion nicht als gescheitert gelten (sonst zeigen die Komponenten einen Fehler-Toast).
 */
describe('AppointmentStore: Aktion gelingt, Neuladen scheitert (#246)', () => {
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

  const cases: [string, string, (id: number) => Promise<void>][] = [
    ['cancelAppointment', 'cancel', (id) => store.cancelAppointment(id)],
    ['approveAppointment', 'participation', (id) => store.approveAppointment(id)],
    ['rejectAppointment', 'participation', (id) => store.rejectAppointment(id)],
    ['sendMessage', 'messages', (id) => store.sendMessage(id, 'Hallo')],
  ];

  for (const [name, path, run] of cases) {
    it(`${name}: POST 200 + Neuladen 404 wirft keinen Fehler`, async () => {
      const result = run(351);
      http
        .expectOne({ method: 'POST', url: `/api/v2/appointments/351/${path}` })
        .flush(null, { status: 200, statusText: 'OK' });
      await new Promise((r) => setTimeout(r));
      http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });
      await new Promise((r) => setTimeout(r));
      http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });

      let thrown: unknown = null;
      await result.catch((err: unknown) => (thrown = err));
      expect(
        thrown,
        `BUG #246: ${name} meldet Fehler, obwohl die Aktion gelang (Neuladen: ${
          thrown instanceof HttpErrorResponse ? thrown.status : thrown
        })`,
      ).toBeNull();
    });
  }

  it('cancelAppointment: schreibt den Status lokal fort, wenn auch der Retry scheitert', async () => {
    const base = { id: 351, status: 'PLANNED' } as Appointment;
    store.appointments.set([base]);
    store.currentAppointment.set(base);
    const result = store.cancelAppointment(351);
    http
      .expectOne({ method: 'POST', url: '/api/v2/appointments/351/cancel' })
      .flush(null, { status: 200, statusText: 'OK' });
    await new Promise((r) => setTimeout(r));
    http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });
    await new Promise((r) => setTimeout(r));
    http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });
    await result;
    expect(store.currentAppointment()?.status).toBe('CANCELLED');
    expect(store.appointments()[0].status).toBe('CANCELLED');
  });
  it('approveAppointment: schreibt die eigene Teilnahme lokal fort und lässt error() leer', async () => {
    const auth = TestBed.inject(AuthService);
    vi.spyOn(auth, 'user').mockReturnValue({ id: 7 } as never);
    const base = {
      id: 351,
      status: 'PLANNED',
      participants: [
        { user_id: 7, status: 'PENDING' },
        { user_id: 8, status: 'PENDING' },
      ],
    } as unknown as Appointment;
    store.appointments.set([base]);
    store.currentAppointment.set(base);
    const result = store.approveAppointment(351);
    http
      .expectOne({ method: 'POST', url: '/api/v2/appointments/351/participation' })
      .flush(null, { status: 200, statusText: 'OK' });
    await new Promise((r) => setTimeout(r));
    http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });
    await new Promise((r) => setTimeout(r));
    http.expectOne(DETAIL).flush(null, { status: 404, statusText: 'Not Found' });
    await result;
    const statuses = store.currentAppointment()?.participants.map((p) => p.status);
    expect(statuses).toEqual(['APPROVED', 'PENDING']);
    expect(store.error()).toBeNull();
  });
});
