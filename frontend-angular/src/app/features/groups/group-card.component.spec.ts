import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ModalService } from '../../core/modal.service';
import { Group, User } from '../../core/models';
import { click } from '../appointment/testing';
import { GroupCardComponent } from './group-card.component';

const member = (id: number): User => ({ id, first_name: `Anna${id}`, last_name: `Berg${id}`, email: null });
const group = (members: User[]): Group => ({ id: 3, name: 'Leiterrunde', members });

describe('GroupCardComponent', () => {
  const modal = { confirm: vi.fn() };

  const render = (value: Group) => {
    modal.confirm.mockReset().mockResolvedValue(true);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: ModalService, useValue: modal }] });
    const fixture = TestBed.createComponent(GroupCardComponent);
    fixture.componentRef.setInput('group', value);
    const deleted: number[] = [];
    fixture.componentInstance.deleted.subscribe((id) => deleted.push(id));
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, deleted };
  };

  it('verlinkt auf die Detailseite und zeigt Namen und Mitgliederzahl (Singular)', () => {
    const { el } = render(group([member(1)]));
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/groups/3');
    expect(el.textContent).toContain('Leiterrunde');
    expect(el.textContent).toContain('1 Mitglied');
    expect(el.textContent).not.toContain('1 Mitglieder');
  });

  it('zeigt höchstens fünf Initialen und den Rest als „+n“', () => {
    const { el } = render(group([1, 2, 3, 4, 5, 6, 7].map(member)));
    expect(el.textContent).toContain('7 Mitglieder');
    expect(el.querySelectorAll('[data-testid="member-avatar"]')).toHaveLength(5);
    expect(el.querySelector('[data-testid="member-avatar"]')?.textContent?.trim()).toBe('AB');
    expect(el.textContent).toContain('+2');
  });

  it('zeigt ohne Mitglieder keine Avatare', () => {
    const { el } = render(group([]));
    expect(el.textContent).toContain('0 Mitglieder');
    expect(el.querySelector('[data-testid="member-avatar"]')).toBeNull();
  });

  it('fragt vor dem Löschen nach und gibt die Gruppen-ID aus, ohne zu navigieren', async () => {
    const { el, deleted } = render(group([member(1)]));
    const event = new MouseEvent('click', { bubbles: true, cancelable: true });
    el.querySelector('button')?.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    await new Promise((resolve) => setTimeout(resolve));
    expect(modal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Gruppe löschen',
        message: "Möchtest du die Gruppe 'Leiterrunde' wirklich löschen? Alle Mitglieder werden entfernt.",
        confirmText: 'Löschen',
        confirmColor: 'red',
      }),
    );
    expect(deleted).toEqual([3]);
  });

  it('löscht nicht, wenn der Dialog abgebrochen wird', async () => {
    const { el, deleted } = render(group([]));
    modal.confirm.mockResolvedValue(false);
    click(el.querySelector('button'));
    await new Promise((resolve) => setTimeout(resolve));
    expect(deleted).toEqual([]);
  });
});
