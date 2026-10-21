import { TestBed } from '@angular/core/testing';
import { Friend } from '../../core/models';
import { buttonByText, click } from '../appointment/testing';
import { FriendshipStore } from '../friends/friendship.store';
import { AddGroupMemberDialogComponent } from './add-group-member-dialog.component';

const friend = (id: number, name: string): Friend => ({ user_id: id, name, email: `${name}@x.de`, profile_picture_url: '', friends_since: '' });

describe('AddGroupMemberDialogComponent', () => {
  const friends = { fetchFriends: vi.fn() };

  const render = (existing: number[] = []) => {
    friends.fetchFriends.mockReset().mockResolvedValue([friend(1, 'Anna'), friend(2, 'Annika'), friend(3, 'Bernd')]);
    TestBed.configureTestingModule({ providers: [{ provide: FriendshipStore, useValue: friends }] });
    const fixture = TestBed.createComponent(AddGroupMemberDialogComponent);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('existingMemberIds', existing);
    const closed = vi.fn();
    const added: number[] = [];
    fixture.componentInstance.closed.subscribe(closed);
    fixture.componentInstance.added.subscribe((id) => added.push(id));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = async (value: string) => {
      const input = el.querySelector('input') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await new Promise((resolve) => setTimeout(resolve));
      fixture.detectChanges();
    };
    const results = () => Array.from(el.querySelectorAll('[data-testid="result"]'));
    return { fixture, el, type, results, closed, added };
  };

  it('sucht erst ab zwei Zeichen', async () => {
    const { type, results, el } = render();
    await type('A');
    expect(friends.fetchFriends).not.toHaveBeenCalled();
    expect(results()).toHaveLength(0);
    expect(el.textContent).not.toContain('Suchergebnisse');
  });

  it('filtert Freunde nach Namen und lädt die Liste nur einmal', async () => {
    const { type, results } = render();
    await type('an');
    expect(results()).toHaveLength(2);
    await type('ann');
    expect(results()).toHaveLength(2);
    await type('ber');
    expect(results()).toHaveLength(1);
    expect(friends.fetchFriends).toHaveBeenCalledTimes(1);
  });

  it('blendet bestehende Mitglieder aus', async () => {
    const { type, results } = render([1]);
    await type('an');
    expect(results().map((r) => r.textContent)).toEqual([expect.stringContaining('Annika')]);
  });

  it('zeigt „Keine Freunde gefunden“ ohne Treffer, auch bei Ladefehler', async () => {
    const { type, el } = render();
    await type('zz');
    expect(el.textContent).toContain('Keine Freunde gefunden');
  });

  it('fügt die gewählte Person hinzu und schließt', async () => {
    const { type, results, el, added, closed, fixture } = render();
    expect(buttonByText(el, 'Hinzufügen')?.disabled).toBe(true);
    await type('bern');
    click(results()[0]);
    fixture.detectChanges();
    expect(el.textContent).toContain('Wird zur Gruppe hinzugefügt');
    click(buttonByText(el, 'Hinzufügen'));
    expect(added).toEqual([3]);
    expect(closed).toHaveBeenCalled();
  });

  it('schließt mit Abbrechen und Escape', () => {
    const { el, closed } = render();
    click(buttonByText(el, 'Abbrechen'));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(closed).toHaveBeenCalledTimes(2);
  });
});
