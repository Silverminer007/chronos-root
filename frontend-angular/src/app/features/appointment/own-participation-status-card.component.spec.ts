import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AppointmentStore } from '../agenda/appointment.store';
import { AuthService } from '../../core/auth.service';
import { User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { OwnParticipationStatusCardComponent } from './own-participation-status-card.component';
import { appointment, buttonByText, click, participant } from './testing';

describe('OwnParticipationStatusCardComponent', () => {
  let store: { approveAppointment: ReturnType<typeof vi.fn>; rejectAppointment: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  const user = signal<User | null>({ id: 1, first_name: 'Max', last_name: 'Muster', email: null });

  const render = (status?: 'APPROVED' | 'REJECTED' | 'PENDING') => {
    store = {
      approveAppointment: vi.fn().mockResolvedValue(undefined),
      rejectAppointment: vi.fn().mockResolvedValue(undefined),
    };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: AuthService, useValue: { user } },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(OwnParticipationStatusCardComponent);
    fixture.componentRef.setInput(
      'appointment',
      appointment({ participants: status ? [participant(1, { status }), participant(2)] : [participant(2)] }),
    );
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('zeigt bei ausstehender Antwort den Hinweis und beide Buttons aktiv', () => {
    const { el } = render('PENDING');
    expect(el.textContent).toContain('Bitte bestätige deine Teilnahme');
    expect(buttonByText(el, 'Zusagen')?.disabled).toBe(false);
    expect(buttonByText(el, 'Absagen')?.disabled).toBe(false);
  });

  it('behandelt fehlende eigene Teilnahme wie „ausstehend“', () => {
    const { el } = render();
    expect(el.textContent).toContain('Bitte bestätige deine Teilnahme');
  });

  it('deaktiviert „Zusagen“ nach einer Zusage und blendet den Hinweis aus', () => {
    const { el } = render('APPROVED');
    expect(buttonByText(el, 'Zusagen')?.disabled).toBe(true);
    expect(buttonByText(el, 'Absagen')?.disabled).toBe(false);
    expect(el.textContent).not.toContain('Bitte bestätige deine Teilnahme');
  });

  it('deaktiviert „Absagen“ nach einer Absage', () => {
    const { el } = render('REJECTED');
    expect(buttonByText(el, 'Absagen')?.disabled).toBe(true);
    expect(buttonByText(el, 'Zusagen')?.disabled).toBe(false);
  });

  it('sagt zu und zeigt einen Toast', async () => {
    const { fixture, el } = render('PENDING');
    click(buttonByText(el, 'Zusagen'));
    await fixture.whenStable();
    expect(store.approveAppointment).toHaveBeenCalledWith(5);
    expect(toast.success).toHaveBeenCalledWith('Zusage bestätigt');
  });

  it('sagt ab und zeigt einen Toast', async () => {
    const { fixture, el } = render('PENDING');
    click(buttonByText(el, 'Absagen'));
    await fixture.whenStable();
    expect(store.rejectAppointment).toHaveBeenCalledWith(5);
    expect(toast.success).toHaveBeenCalledWith('Absage registriert');
  });

  it('meldet einen Fehler beim Aktualisieren', async () => {
    const { fixture, el } = render('PENDING');
    store.approveAppointment.mockRejectedValue(new Error('x'));
    click(buttonByText(el, 'Zusagen'));
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Teilnahmestatus konnte nicht aktualisiert werden');
  });
});
