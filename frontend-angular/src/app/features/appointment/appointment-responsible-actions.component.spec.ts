import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { User } from '../../core/models';
import { ModalService } from '../../core/modal.service';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { AppointmentResponsibleActionsComponent } from './appointment-responsible-actions.component';
import { appointment, buttonByText, click, participant } from './testing';

describe('AppointmentResponsibleActionsComponent', () => {
  const user = signal<User | null>(null);
  const store = { cancelAppointment: vi.fn() };
  const modal = { confirm: vi.fn() };
  const toast = { success: vi.fn(), error: vi.fn() };

  beforeEach(() => {
    store.cancelAppointment.mockReset().mockResolvedValue(undefined);
    modal.confirm.mockReset().mockResolvedValue(true);
    toast.success.mockReset();
    toast.error.mockReset();
  });

  const render = (userId: number, status: 'PLANNED' | 'CANCELLED' = 'PLANNED') => {
    user.set({ id: userId, first_name: 'M', last_name: 'M', email: null });
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user } },
        { provide: AppointmentStore, useValue: store },
        { provide: ModalService, useValue: modal },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(AppointmentResponsibleActionsComponent);
    fixture.componentRef.setInput(
      'appointment',
      appointment({
        status,
        participants: [participant(1, { role: 'RESPONSIBLE' }), participant(2)],
      }),
    );
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('zeigt nichts für Nicht-Organisatoren', () => {
    expect(render(2).el.textContent?.trim()).toBe('');
  });

  it('zeigt Organisatoren die Aktion „Termin absagen“', () => {
    const { el } = render(1);
    expect(el.textContent).toContain('Organisator-Aktionen');
    expect(buttonByText(el, 'Termin absagen')?.disabled).toBe(false);
  });

  it('deaktiviert „Termin absagen“ bei bereits abgesagten Terminen', () => {
    expect(buttonByText(render(1, 'CANCELLED').el, 'Termin absagen')?.disabled).toBe(true);
  });

  it('sagt den Termin nach Bestätigung ab und meldet den Erfolg', async () => {
    const { fixture, el } = render(1);
    click(buttonByText(el, 'Termin absagen'));
    await fixture.whenStable();
    expect(modal.confirm).toHaveBeenCalledWith({
      title: 'Termin absagen',
      message: 'Möchtest du diesen Termin wirklich absagen? Alle Teilnehmer werden benachrichtigt.',
      confirmText: 'Termin absagen',
      confirmColor: 'red',
    });
    expect(store.cancelAppointment).toHaveBeenCalledWith(5);
    expect(toast.success).toHaveBeenCalledWith(
      'Termin abgesagt',
      'Alle Teilnehmer wurden benachrichtigt',
    );
  });

  it('sagt nichts ab, wenn der Dialog abgebrochen wird', async () => {
    modal.confirm.mockResolvedValue(false);
    const { fixture, el } = render(1);
    click(buttonByText(el, 'Termin absagen'));
    await fixture.whenStable();
    expect(store.cancelAppointment).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('meldet einen Fehler beim Absagen', async () => {
    store.cancelAppointment.mockRejectedValue(new Error('x'));
    const { fixture, el } = render(1);
    click(buttonByText(el, 'Termin absagen'));
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Termin konnte nicht abgesagt werden');
  });
});
