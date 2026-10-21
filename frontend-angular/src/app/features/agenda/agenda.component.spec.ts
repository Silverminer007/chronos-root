import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { Appointment } from '../../core/models';
import { CreateAppointmentStepperComponent } from '../appointment-form/create-appointment-stepper.component';
import { AgendaComponent } from './agenda.component';

const appointment = (id: number): Appointment => ({
  id,
  name: `Termin ${id}`,
  description: null,
  start: '2026-10-21T16:00:00Z',
  end: '2026-10-21T19:00:00Z',
  venue: null,
  status: 'PLANNED',
  minimal_attendees: 0,
  participants: [],
  group_participants: [],
  messages: [],
});

const AGENDA = '/api/v2/appointments?page=0&size=20&participants=true';

describe('AgendaComponent', () => {
  let fixture: ComponentFixture<AgendaComponent>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AgendaComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const respond = async (body: object, init?: { status: number; statusText: string }) => {
    http.expectOne(AGENDA).flush(body, init);
    await fixture.whenStable();
    fixture.detectChanges();
  };

  it('zeigt beim Laden fünf Skeleton-Karten', () => {
    expect(el().querySelectorAll('app-appointment-card-skeleton')).toHaveLength(5);
    http.expectOne(AGENDA);
  });

  it('zeigt die geladenen Termine als Karten', async () => {
    await respond({
      data: [appointment(1), appointment(2)],
      meta: { page: 0, size: 20, total: 2 },
    });
    expect(el().querySelectorAll('app-appointment-card')).toHaveLength(2);
    expect(el().querySelector('app-appointment-card-skeleton')).toBeNull();
    expect(el().textContent).not.toContain('Mehr laden');
  });

  it('zeigt den Leerzustand ohne Termine', async () => {
    await respond({ data: [], meta: { page: 0, size: 20, total: 0 } });
    expect(el().textContent).toContain('Keine Termine vorhanden');
    expect(el().textContent).toContain(
      'Erstelle deinen ersten Termin oder warte auf eine Einladung.',
    );
  });

  it('zeigt den Fehlerzustand statt des Leerzustands', async () => {
    await respond({ message: 'Kaputt' }, { status: 500, statusText: 'Server Error' });
    expect(el().textContent).toContain('Fehler beim Laden');
    expect(el().textContent).toContain('Kaputt');
    expect(el().textContent).not.toContain('Keine Termine vorhanden');
  });

  it('lädt über „Mehr laden“ die nächste Seite nach', async () => {
    await respond({ data: [appointment(1)], meta: { page: 0, size: 20, total: 21 } });
    const more = Array.from(el().querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Mehr laden'),
    );
    more?.click();
    http
      .expectOne('/api/v2/appointments?page=1&size=20&participants=true')
      .flush({ data: [appointment(2)], meta: { page: 1, size: 20, total: 21 } });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el().querySelectorAll('app-appointment-card')).toHaveLength(2);
    expect(el().textContent).not.toContain('Mehr laden');
  });

  it('enthält den Suchkopf und den Anlegen-Button', async () => {
    await respond({ data: [], meta: { page: 0, size: 20, total: 0 } });
    expect(el().querySelector('app-search-header')).not.toBeNull();
    expect(el().querySelector('button.fab[aria-label="Termin erstellen"]')).not.toBeNull();
  });

  it('öffnet über den Anlegen-Button den Stepper und springt nach dem Anlegen zum Termin', async () => {
    await respond({ data: [], meta: { page: 0, size: 20, total: 0 } });
    expect(el().querySelector('.stepper')).toBeNull();
    (el().querySelector('.fab') as HTMLElement).click();
    fixture.detectChanges();
    expect(el().querySelector('.stepper')).not.toBeNull();

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.debugElement
      .query(By.directive(CreateAppointmentStepperComponent))
      .componentInstance.created.emit(42);
    expect(navigate).toHaveBeenCalledWith('/appointment/42');
  });
});
