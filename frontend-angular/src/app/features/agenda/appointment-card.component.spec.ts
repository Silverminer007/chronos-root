import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Appointment, User, UserParticipant } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentCardComponent } from './appointment-card.component';
import { AppointmentStore } from './appointment.store';

const participant = (id: number, status: UserParticipant['status'], name = `Person ${id}`): UserParticipant => ({
  user_id: id,
  name,
  profile_picture_url: '',
  role: 'ATTENDANT',
  status,
  via_group_id: null,
  via_group_name: null,
});

const base: Appointment = {
  id: 5,
  name: 'Zeltlager',
  description: 'Packliste beachten',
  start: '2026-10-21T16:00:00Z',
  end: '2026-10-21T19:00:00Z',
  venue: 'Waldwiese',
  status: 'PLANNED',
  minimal_attendees: 3,
  participants: [],
  group_participants: [],
  messages: [],
};

describe('AppointmentCardComponent', () => {
  let fixture: ComponentFixture<AppointmentCardComponent>;
  let store: { approveAppointment: ReturnType<typeof vi.fn>; rejectAppointment: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  const user = signal<User | null>({ id: 1, first_name: 'Max', last_name: 'Muster', email: 'm@example.org' });

  const el = () => fixture.nativeElement as HTMLElement;
  const render = (appointment: Partial<Appointment> = {}) => {
    fixture.componentRef.setInput('appointment', { ...base, ...appointment });
    fixture.detectChanges();
  };
  const button = (label: string) =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes(label)) as HTMLButtonElement;

  beforeEach(() => {
    user.set({ id: 1, first_name: 'Max', last_name: 'Muster', email: 'm@example.org' });
    store = {
      approveAppointment: vi.fn().mockResolvedValue(undefined),
      rejectAppointment: vi.fn().mockResolvedValue(undefined),
    };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AppointmentStore, useValue: store },
        { provide: AuthService, useValue: { user } },
        { provide: ToastService, useValue: toast },
      ],
    });
    fixture = TestBed.createComponent(AppointmentCardComponent);
  });

  it('zeigt Name, Zeitraum, Ort und Beschreibung', () => {
    render();
    expect(el().querySelector('h3')?.textContent).toContain('Zeltlager');
    expect(el().textContent).toContain('Mi., 21.10.2026');
    expect(el().textContent).toContain('Waldwiese');
    expect(el().textContent).toContain('Packliste beachten');
  });

  it('verlinkt auf die Termin-Detailseite', () => {
    render();
    expect(el().querySelector('a')?.getAttribute('href')).toBe('/appointment/5');
  });

  it('lässt Ort und Beschreibung ohne Werte weg', () => {
    render({ venue: null, description: null });
    expect(el().querySelector('.bi-geo-alt')).toBeNull();
    expect(el().querySelector('.line-clamp-2')).toBeNull();
  });

  it('zeigt das Banner „Termin abgesagt“', () => {
    render({ status: 'CANCELLED' });
    expect(el().querySelector('.status-banner')?.textContent).toContain('Termin abgesagt');
  });

  it('zeigt das Banner „Zu wenig Teilnehmende“', () => {
    render({ status: 'NOT_ENOUGH_ATTENDEES' });
    expect(el().querySelector('.status-banner')?.textContent).toContain('Zu wenig Teilnehmende');
  });

  it('zeigt kein Banner für geplante Termine', () => {
    render();
    expect(el().querySelector('.status-banner')).toBeNull();
  });

  it('zeigt Zusagen gegen das Minimum und höchstens vier Avatare', () => {
    render({
      participants: [1, 2, 3, 4, 5, 6].map((id) => participant(id, 'APPROVED')).concat(participant(7, 'PENDING')),
    });
    expect(el().querySelector('.attendance')?.textContent).toContain('6/3');
    expect(el().querySelectorAll('.avatar:not(.avatar-more)')).toHaveLength(4);
    expect(el().querySelector('.avatar-more')?.textContent).toContain('+2');
  });

  it('zeigt ohne Mindestteilnehmerzahl keine Zählung', () => {
    render({ minimal_attendees: 0 });
    expect(el().querySelector('.attendance')).toBeNull();
  });

  it('sperrt „Zusagen“, wenn der Benutzer schon zugesagt hat', () => {
    render({ participants: [participant(1, 'APPROVED')] });
    expect(button('Zusagen').disabled).toBe(true);
    expect(button('Absagen').disabled).toBe(false);
  });

  it('sperrt „Absagen“, wenn der Benutzer schon abgesagt hat', () => {
    render({ participants: [participant(1, 'REJECTED')] });
    expect(button('Absagen').disabled).toBe(true);
    expect(button('Zusagen').disabled).toBe(false);
  });

  it('sagt zu und zeigt einen Toast', async () => {
    render();
    button('Zusagen').click();
    await fixture.whenStable();
    expect(store.approveAppointment).toHaveBeenCalledWith(5);
    expect(toast.success).toHaveBeenCalledWith('Zusage bestätigt');
  });

  it('sagt ab und zeigt einen Toast', async () => {
    render();
    button('Absagen').click();
    await fixture.whenStable();
    expect(store.rejectAppointment).toHaveBeenCalledWith(5);
    expect(toast.success).toHaveBeenCalledWith('Absage registriert');
  });

  it('meldet einen Fehler beim Zusagen als Toast', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    store.approveAppointment.mockRejectedValue(new Error('x'));
    render();
    button('Zusagen').click();
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Teilnahmestatus konnte nicht aktualisiert werden');
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('meldet einen Fehler beim Absagen als Toast', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    store.rejectAppointment.mockRejectedValue(new Error('x'));
    render();
    button('Absagen').click();
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Teilnahmestatus konnte nicht aktualisiert werden');
  });

  it('tut ohne Benutzer nichts', async () => {
    user.set(null);
    render();
    button('Zusagen').click();
    button('Absagen').click();
    await fixture.whenStable();
    expect(store.approveAppointment).not.toHaveBeenCalled();
    expect(store.rejectAppointment).not.toHaveBeenCalled();
  });
});
