import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { FriendshipStore } from './friendship.store';
import { SendFriendRequestDialogComponent } from './send-friend-request-dialog.component';

describe('SendFriendRequestDialogComponent', () => {
  const store = { sendRequest: vi.fn() };
  const toast = { success: vi.fn() };

  const render = (visible = true) => {
    store.sendRequest.mockReset().mockResolvedValue(undefined);
    toast.success.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: FriendshipStore, useValue: store },
        { provide: ToastService, useValue: toast },
      ],
    });
    const fixture = TestBed.createComponent(SendFriendRequestDialogComponent);
    fixture.componentRef.setInput('visible', visible);
    let closed = 0;
    fixture.componentInstance.closed.subscribe(() => closed++);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = (value: string) => {
      const input = el.querySelector('input') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    return { fixture, el, type, closed: () => closed };
  };

  it('rendert nichts, solange er nicht sichtbar ist', () => {
    const { el } = render(false);
    expect(el.querySelector('.modal')).toBeNull();
  });

  it('sperrt „Anfrage senden“ bei ungültiger E-Mail', () => {
    const { el, type } = render();
    expect(buttonByText(el, 'Anfrage senden')?.disabled).toBe(true);
    type('keine-mail');
    expect(buttonByText(el, 'Anfrage senden')?.disabled).toBe(true);
    type('a@b.de');
    expect(buttonByText(el, 'Anfrage senden')?.disabled).toBe(false);
  });

  it('sendet, zeigt einen Toast und schließt', async () => {
    const { fixture, el, type, closed } = render();
    type('a@b.de');
    click(buttonByText(el, 'Anfrage senden'));
    await fixture.whenStable();
    expect(store.sendRequest).toHaveBeenCalledWith('a@b.de');
    expect(toast.success).toHaveBeenCalledWith('Anfrage gesendet', 'Freundschaftsanfrage an a@b.de wurde gesendet');
    expect(closed()).toBe(1);
  });

  it('sendet auch mit Enter', async () => {
    const { fixture, el, type } = render();
    type('a@b.de');
    el.querySelector('input')?.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter' }));
    await fixture.whenStable();
    expect(store.sendRequest).toHaveBeenCalledWith('a@b.de');
  });

  it('zeigt die Backend-Meldung bei Fehler und bleibt offen', async () => {
    const { fixture, el, type, closed } = render();
    store.sendRequest.mockRejectedValue(new HttpErrorResponse({ status: 404, error: { message: 'Nutzer nicht gefunden' } }));
    type('a@b.de');
    click(buttonByText(el, 'Anfrage senden'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Nutzer nicht gefunden');
    expect(closed()).toBe(0);
  });

  it('nutzt den deutschen Fallback ohne Backend-Meldung', async () => {
    const { fixture, el, type } = render();
    store.sendRequest.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    type('a@b.de');
    click(buttonByText(el, 'Anfrage senden'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Fehler beim Senden der Anfrage. Überprüfe die E-Mail-Adresse.');
  });

  it('meldet Abbrechen und Escape und leert die Eingabe', () => {
    const { fixture, el, type, closed } = render();
    type('a@b.de');
    click(buttonByText(el, 'Abbrechen'));
    expect(closed()).toBe(1);
    fixture.detectChanges();
    expect((el.querySelector('input') as HTMLInputElement).value).toBe('');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(closed()).toBe(2);
  });
});
