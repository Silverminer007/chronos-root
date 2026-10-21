import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { User } from '../../core/models';
import { AppointmentResponsibleActionsComponent } from './appointment-responsible-actions.component';
import { appointment, buttonByText, click, participant } from './testing';

describe('AppointmentResponsibleActionsComponent', () => {
  const user = signal<User | null>(null);

  const render = (userId: number, status: 'PLANNED' | 'CANCELLED' = 'PLANNED') => {
    user.set({ id: userId, first_name: 'M', last_name: 'M', email: null });
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: { user } }] });
    const fixture = TestBed.createComponent(AppointmentResponsibleActionsComponent);
    fixture.componentRef.setInput(
      'appointment',
      appointment({ status, participants: [participant(1, { role: 'RESPONSIBLE' }), participant(2)] }),
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

  it('meldet den Wunsch zu Absagen nach außen; das Absagen selbst folgt mit dem Ticket „Termin anlegen und bearbeiten“', () => {
    const { fixture, el } = render(1);
    const requested = vi.fn();
    fixture.componentInstance.cancelRequested.subscribe(requested);
    click(buttonByText(el, 'Termin absagen'));
    expect(requested).toHaveBeenCalled();
  });
});
