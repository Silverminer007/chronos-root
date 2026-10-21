import { TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { MessagesCardComponent } from './messages-card.component';
import { appointment, buttonByText, click, participant } from './testing';

const message = (id: number, timestamp: string, body = `Text ${id}`) => ({
  id,
  sender_id: 1,
  sender_name: `Absender ${id}`,
  appointment_id: 5,
  body,
  timestamp,
});

describe('MessagesCardComponent', () => {
  let store: { sendMessage: ReturnType<typeof vi.fn> };
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  const render = (messages = [message(1, '2026-10-20T10:00:00'), message(2, '2026-10-21T10:00:00')]) => {
    store = { sendMessage: vi.fn().mockResolvedValue(undefined) };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(MessagesCardComponent);
    fixture.componentRef.setInput('appointment', appointment({ messages, participants: [participant(1), participant(2)] }));
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('zeigt Nachrichten, neueste zuerst, mit Absender und Zeitpunkt', () => {
    const { el } = render();
    const items = Array.from(el.querySelectorAll('[data-testid="message"]'));
    expect(items.map((i) => i.querySelector('[data-testid="sender"]')?.textContent)).toEqual(['Absender 2', 'Absender 1']);
    expect(items[0].textContent).toContain('21.10.2026 10:00 Uhr');
    expect(items[0].textContent).toContain('Text 2');
  });

  it('zeigt ohne Nachrichten den Leerzustand', () => {
    const { el } = render([]);
    expect(el.textContent).toContain('Noch keine Nachrichten');
  });

  it('öffnet den Dialog mit Termintitel und Empfängerzahl', () => {
    const { fixture, el } = render();
    expect(el.querySelector('app-message-dialog .modal')).toBeNull();
    click(buttonByText(el, 'Nachricht senden'));
    fixture.detectChanges();
    const dialog = el.querySelector('app-message-dialog') as HTMLElement;
    expect(dialog.textContent).toContain('Zeltlager');
    expect(dialog.textContent).toContain('(2 Personen)');
  });

  it('sendet die Nachricht, zeigt einen Toast und schließt den Dialog', async () => {
    const { fixture, el } = render();
    click(buttonByText(el, 'Nachricht senden'));
    fixture.detectChanges();
    const dialog = fixture.debugElement.query((d) => d.name === 'app-message-dialog');
    dialog.componentInstance.sent.emit({ message: 'Hallo' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(store.sendMessage).toHaveBeenCalledWith(5, 'Hallo');
    expect(toast.success).toHaveBeenCalledWith('Nachricht versendet');
    expect(el.querySelector('app-message-dialog .modal')).toBeNull();
  });

  it('meldet Fehler beim Senden und lässt den Dialog offen', async () => {
    const { fixture, el } = render();
    store.sendMessage.mockRejectedValue(new Error('x'));
    click(buttonByText(el, 'Nachricht senden'));
    fixture.detectChanges();
    const dialog = fixture.debugElement.query((d) => d.name === 'app-message-dialog');
    dialog.componentInstance.sent.emit({ message: 'Hallo' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(toast.error).toHaveBeenCalledWith('Fehler beim Senden', 'Bitte kontaktiere den Entwickler der App');
    expect(el.querySelector('app-message-dialog .modal')).not.toBeNull();
  });
});
