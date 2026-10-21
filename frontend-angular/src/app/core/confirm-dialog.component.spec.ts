import { TestBed } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { ModalService } from './modal.service';

describe('ConfirmDialogComponent', () => {
  const setup = async () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    const modal = TestBed.inject(ModalService);
    const el = fixture.nativeElement as HTMLElement;
    const settle = () => fixture.whenStable();
    await settle();
    return { modal, el, settle };
  };

  it('rendert nichts, solange kein Dialog angefordert ist', async () => {
    const { el } = await setup();
    expect(el.querySelector('.modal')).toBeNull();
  });

  it('zeigt Titel, Text und Bestätigungstext auf Deutsch', async () => {
    const { modal, el, settle } = await setup();
    void modal.confirm({ title: 'Termin löschen', message: 'Wirklich?', confirmText: 'Löschen', confirmColor: 'red' });
    await settle();
    expect(el.querySelector('.modal-title')?.textContent).toContain('Termin löschen');
    expect(el.querySelector('.modal-body')?.textContent).toContain('Wirklich?');
    const buttons = el.querySelectorAll('.modal-footer button');
    expect(buttons[0].textContent).toContain('Abbrechen');
    expect(buttons[1].textContent).toContain('Löschen');
    expect(buttons[1].classList).toContain('btn-danger');
  });

  it('bestätigt per Button', async () => {
    const { modal, el, settle } = await setup();
    const result = modal.confirm({ title: 'T', message: 'M', confirmText: 'Ja' });
    await settle();
    (el.querySelectorAll('.modal-footer button')[1] as HTMLButtonElement).click();
    expect(await result).toBe(true);
    await settle();
    expect(el.querySelector('.modal')).toBeNull();
  });

  it('bricht per Abbrechen und Escape ab', async () => {
    const { modal, el, settle } = await setup();
    for (const close of [
      () => (el.querySelectorAll('.modal-footer button')[0] as HTMLButtonElement).click(),
      () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })),
    ]) {
      const result = modal.confirm({ title: 'T', message: 'M', confirmText: 'Ja' });
      await settle();
      close();
      expect(await result).toBe(false);
      await settle();
    }
  });

  it('setzt Fokus auf Bestätigen, hält Tab im Dialog und stellt den Fokus wieder her', async () => {
    const { modal, el, settle } = await setup();
    document.body.appendChild(el);
    const opener = document.createElement('button');
    document.body.appendChild(opener);
    opener.focus();
    const result = modal.confirm({ title: 'T', message: 'M', confirmText: 'Ja' });
    await settle();
    const [cancel, confirm] = Array.from(el.querySelectorAll<HTMLButtonElement>('.modal-footer button'));
    expect(document.activeElement).toBe(confirm);

    const tab = (shiftKey: boolean) => {
      const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, cancelable: true });
      document.dispatchEvent(event);
      return event.defaultPrevented;
    };
    expect(tab(false)).toBe(true);
    expect(document.activeElement).toBe(cancel);
    expect(tab(true)).toBe(true);
    expect(document.activeElement).toBe(confirm);
    opener.focus();
    expect(tab(false)).toBe(true);
    expect(document.activeElement).toBe(cancel);

    confirm.click();
    await result;
    await settle();
    expect(document.activeElement).toBe(opener);
    opener.remove();
    el.remove();
  });
});
