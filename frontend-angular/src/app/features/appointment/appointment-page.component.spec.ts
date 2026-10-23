import { Location } from '@angular/common';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Appointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { AppointmentPageComponent } from './appointment-page.component';
import { GroupStore } from '../groups/group.store';
import { appointment, buttonByText, click } from './testing';

describe('AppointmentPageComponent', () => {
  const current = signal<Appointment | null>(null);
  let store: { currentAppointment: typeof current; fetchAppointment: ReturnType<typeof vi.fn>; resetCurrentAppointment: ReturnType<typeof vi.fn> };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  const render = (fetchAppointment: () => Promise<unknown>) => {
    current.set(null);
    store = { currentAppointment: current, fetchAppointment: vi.fn(fetchAppointment), resetCurrentAppointment: vi.fn(() => current.set(null)) };
    router = { navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: AuthService, useValue: { user: signal(null) } },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } },
        { provide: GroupStore, useValue: {} },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '5' })) } },
        { provide: Router, useValue: router },
        { provide: Location, useValue: { back: vi.fn() } },
      ],
    });
    vi.spyOn(history, 'state', 'get').mockReturnValue({ navigationId: 1 });
    const fixture = TestBed.createComponent(AppointmentPageComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  afterEach(() => vi.restoreAllMocks());

  it('lädt den Termin aus der Route und zeigt währenddessen einen Ladehinweis', async () => {
    let resolve!: (a: Appointment) => void;
    const { fixture, el } = render(() => new Promise<Appointment>((r) => (resolve = r)));
    expect(store.fetchAppointment).toHaveBeenCalledWith(5);
    expect(el.textContent).toContain('Termin wird geladen...');

    current.set(appointment());
    resolve(appointment());
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.textContent).not.toContain('Termin wird geladen...');
  });

  it('zeigt nach dem Laden alle Karten', async () => {
    const { fixture, el } = render(async () => current.set(appointment()));
    await fixture.whenStable();
    fixture.detectChanges();

    for (const selector of [
      'app-appointment-header',
      'app-appointment-details-card',
      'app-own-participation-status-card',
      'app-participants-status-card',
      'app-group-participants-card',
      'app-appointment-responsible-actions',
      'app-messages-card',
    ]) {
      expect(el.querySelectorAll(selector), selector).toHaveLength(1);
    }
  });

  it('zeigt bei einem Ladefehler die Fehlerseite und führt zur Agenda zurück', async () => {
    const { fixture, el } = render(() => Promise.reject(new Error('404')));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.textContent).toContain('Termin nicht gefunden');
    expect(el.textContent).toContain('Termin konnte nicht geladen werden');
    expect(el.querySelector('app-appointment-header')).toBeNull();
    click(buttonByText(el, 'Zurück zur Übersicht'));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/agenda');
  });

  it('leert den aktuellen Termin beim Verlassen der Seite', () => {
    const { fixture } = render(async () => current.set(appointment()));
    store.resetCurrentAppointment.mockClear();
    fixture.destroy();
    expect(store.resetCurrentAppointment).toHaveBeenCalled();
  });
});
