import { TestBed } from '@angular/core/testing';
import { User } from '../../core/models';
import { click } from '../appointment/testing';
import { GroupMemberCardComponent } from './group-member-card.component';

const member = (overrides: Partial<User> = {}): User => ({ id: 7, first_name: 'Anna', last_name: 'Berg', email: null, ...overrides });

describe('GroupMemberCardComponent', () => {
  const render = (value: User, canRemove: boolean) => {
    const fixture = TestBed.createComponent(GroupMemberCardComponent);
    fixture.componentRef.setInput('member', value);
    fixture.componentRef.setInput('canRemove', canRemove);
    const removed: number[] = [];
    fixture.componentInstance.remove.subscribe((id) => removed.push(id));
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, removed };
  };

  it('zeigt Initialen und vollen Namen', () => {
    const { el } = render(member(), true);
    expect(el.querySelector('.avatar')?.textContent?.trim()).toBe('AB');
    expect(el.textContent).toContain('Anna Berg');
  });

  it('kommt bei fehlenden Namen ohne Fehler aus', () => {
    const { el } = render(member({ first_name: null, last_name: null }), false);
    expect(el.querySelector('.avatar')?.textContent?.trim()).toBe('');
  });

  it('gibt beim Entfernen die Benutzer-ID aus', () => {
    const { el, removed } = render(member(), true);
    click(el.querySelector('button[title="Mitglied entfernen"]'));
    expect(removed).toEqual([7]);
  });

  it('blendet die Schaltfläche aus, wenn nicht entfernt werden darf', () => {
    const { el } = render(member(), false);
    expect(el.querySelector('button')).toBeNull();
  });
});
