import { ComponentRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageDialogComponent } from './message-dialog.component';

describe('MessageDialogComponent', () => {
  let ref: ComponentRef<MessageDialogComponent>;
  let el: HTMLElement;
  let sent: { message: string }[];
  let closed: number;

  async function create(visible: boolean) {
    const fixture = TestBed.createComponent(MessageDialogComponent);
    ref = fixture.componentRef;
    ref.setInput('visible', visible);
    ref.setInput('appointmentTitle', 'Sommerfest 21.11.2026');
    ref.setInput('recipientCount', 11);
    sent = [];
    closed = 0;
    fixture.componentInstance.sent.subscribe((d) => sent.push(d));
    fixture.componentInstance.closed.subscribe(() => closed++);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
    return fixture;
  }

  const type = async (fixture: Awaited<ReturnType<typeof create>>, value: string) => {
    const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  };
  const sendButton = () => el.querySelector('.btn-gradient') as HTMLButtonElement;

  it('rendert nichts, solange er nicht sichtbar ist', async () => {
    await create(false);
    expect(el.querySelector('.modal')).toBeNull();
  });

  it('zeigt Titel, Empfängerzahl und Zeichenzähler', async () => {
    const fixture = await create(true);
    expect(el.textContent).toContain('Nachricht senden');
    expect(el.textContent).toContain('Sommerfest 21.11.2026');
    expect(el.textContent).toContain('Alle Teilnehmer dieses Termins (11 Personen)');
    await type(fixture, 'Hallo');
    expect(el.textContent).toContain('5 / 500 Zeichen');
  });

  it('erlaubt Senden nur mit nicht-leerem Text bis 500 Zeichen', async () => {
    const fixture = await create(true);
    expect(sendButton().disabled).toBe(true);
    await type(fixture, '   ');
    expect(sendButton().disabled).toBe(true);
    await type(fixture, 'a'.repeat(501));
    expect(sendButton().disabled).toBe(true);
    expect(el.textContent).toContain('Maximale Länge überschritten');
    await type(fixture, 'a'.repeat(500));
    expect(sendButton().disabled).toBe(false);
  });

  it('sendet die Nachricht und leert das Feld', async () => {
    const fixture = await create(true);
    await type(fixture, 'Hallo');
    sendButton().click();
    await fixture.whenStable();
    expect(sent).toEqual([{ message: 'Hallo' }]);
    expect((el.querySelector('textarea') as HTMLTextAreaElement).value).toBe('');
  });

  it('schließt über „Abbrechen“, das X und Escape', async () => {
    const fixture = await create(true);
    (el.querySelector('.btn-outline-secondary') as HTMLButtonElement).click();
    (el.querySelector('.btn-close') as HTMLButtonElement).click();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(closed).toBe(3);
  });
});
