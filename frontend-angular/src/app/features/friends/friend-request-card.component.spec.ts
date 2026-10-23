import { TestBed } from '@angular/core/testing';
import { FriendshipRequest } from '../../core/models';
import { buttonByText, click } from '../appointment/testing';
import { FriendRequestCardComponent } from './friend-request-card.component';

const request: FriendshipRequest = {
  requestId: 4,
  userId: 9,
  userName: 'bernd',
  userEmail: 'b@x.de',
  profilePictureUrl: '',
  status: 'PENDING',
  createdAt: '2026-10-21T18:00:00',
  respondedAt: '',
  isIncoming: true,
};

describe('FriendRequestCardComponent', () => {
  const render = (type: 'incoming' | 'outgoing') => {
    const fixture = TestBed.createComponent(FriendRequestCardComponent);
    fixture.componentRef.setInput('request', request);
    fixture.componentRef.setInput('type', type);
    const events: string[] = [];
    const c = fixture.componentInstance;
    c.accepted.subscribe((id) => events.push(`accept:${id}`));
    c.declined.subscribe((id) => events.push(`decline:${id}`));
    c.cancelled.subscribe((id) => events.push(`cancel:${id}`));
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, events };
  };

  it('zeigt eingehende Anfragen mit Annehmen und Ablehnen', () => {
    const { el } = render('incoming');
    expect(el.textContent).toContain('bernd');
    expect(el.textContent).toContain('Möchte dein Freund werden');
    expect(el.textContent).toContain('21.10.2026 18:00 Uhr');
    expect(el.querySelector('.avatar')?.textContent).toContain('B');
    expect(buttonByText(el, 'Annehmen')).toBeDefined();
    expect(buttonByText(el, 'Zurückziehen')).toBeUndefined();
  });

  it('meldet Annehmen und sperrt danach die Schaltflächen', () => {
    const { fixture, el, events } = render('incoming');
    click(buttonByText(el, 'Annehmen'));
    fixture.detectChanges();
    expect(events).toEqual(['accept:4']);
    expect(buttonByText(el, 'Annehmen')?.disabled).toBe(true);
    expect(buttonByText(el, 'Ablehnen')?.disabled).toBe(true);
  });

  it('meldet Ablehnen', () => {
    const { el, events } = render('incoming');
    click(buttonByText(el, 'Ablehnen'));
    expect(events).toEqual(['decline:4']);
  });

  it('zeigt ausgehende Anfragen mit Zurückziehen', () => {
    const { el, events } = render('outgoing');
    expect(el.textContent).toContain('Anfrage gesendet');
    expect(buttonByText(el, 'Annehmen')).toBeUndefined();
    click(buttonByText(el, 'Zurückziehen'));
    expect(events).toEqual(['cancel:4']);
  });
});
