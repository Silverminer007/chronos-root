import { TestBed } from '@angular/core/testing';
import { Friend } from '../../core/models';
import { AddParticipantDialogComponent } from './add-participant-dialog.component';
import { FriendshipStore } from '../friends/friendship.store';
import { buttonByText, click } from './testing';

const friend = (id: number, name: string): Friend => ({
  user_id: id,
  name,
  email: `${name.toLowerCase()}@example.org`,
  profile_picture_url: '',
  friends_since: '2026-01-01',
});

describe('AddParticipantDialogComponent', () => {
  let lookup: { fetchFriends: ReturnType<typeof vi.fn> };

  const render = (visible = true) => {
    lookup = { fetchFriends: vi.fn().mockResolvedValue([friend(1, 'Anna'), friend(2, 'Annika'), friend(3, 'Bernd')]) };
    TestBed.configureTestingModule({ providers: [{ provide: FriendshipStore, useValue: lookup }] });
    const fixture = TestBed.createComponent(AddParticipantDialogComponent);
    fixture.componentRef.setInput('visible', visible);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = async (value: string) => {
      const input = el.querySelector('input[type="text"]') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      fixture.detectChanges();
    };
    return { fixture, el, type };
  };

  it('rendert nichts, solange der Dialog nicht sichtbar ist', () => {
    const { el } = render(false);
    expect(el.querySelector('.modal')).toBeNull();
  });

  it('sucht erst ab zwei Zeichen (wie Nuxt: davor „Keine Ergebnisse“, ohne die Freunde zu laden)', async () => {
    const { el, type } = render();
    await type('A');
    expect(lookup.fetchFriends).not.toHaveBeenCalled();
    expect(el.querySelectorAll('[data-testid="result"]')).toHaveLength(0);
    expect(el.textContent).toContain('Keine Ergebnisse gefunden');
  });

  it('filtert Freunde nach Namen (ohne Beachtung der Groß-/Kleinschreibung) und lädt die Liste nur einmal', async () => {
    const { el, type } = render();
    await type('ann');
    expect(el.querySelectorAll('[data-testid="result"]')).toHaveLength(2);
    await type('bern');
    expect(el.querySelectorAll('[data-testid="result"]')).toHaveLength(1);
    expect(lookup.fetchFriends).toHaveBeenCalledTimes(1);
  });

  it('zeigt „Keine Ergebnisse gefunden“ ohne Treffer', async () => {
    const { el, type } = render();
    await type('xyz');
    expect(el.textContent).toContain('Keine Ergebnisse gefunden');
  });

  it('deaktiviert „Hinzufügen“ ohne Auswahl', () => {
    const { el } = render();
    expect(buttonByText(el, 'Hinzufügen')?.disabled).toBe(true);
  });

  it('emittiert Person und gewählte Rolle und schließt', async () => {
    const { fixture, el, type } = render();
    const added: unknown[] = [];
    const closed = vi.fn();
    fixture.componentInstance.added.subscribe((e) => added.push(e));
    fixture.componentInstance.closed.subscribe(closed);

    await type('anna');
    click(el.querySelector('[data-testid="result"]'));
    fixture.detectChanges();
    expect(el.textContent).toContain('Als Teilnehmer');
    click(buttonByText(el, 'Helfer'));
    fixture.detectChanges();
    expect(el.textContent).toContain('Als Helfer');
    click(buttonByText(el, 'Hinzufügen'));

    expect(added).toEqual([{ id: 1, role: 'HELPER' }]);
    expect(closed).toHaveBeenCalled();
  });

  it('setzt Suche und Auswahl beim Schließen zurück', async () => {
    const { fixture, el, type } = render();
    await type('anna');
    click(el.querySelector('[data-testid="result"]'));
    fixture.componentRef.setInput('visible', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();
    expect((el.querySelector('input[type="text"]') as HTMLInputElement).value).toBe('');
    expect(buttonByText(el, 'Hinzufügen')?.disabled).toBe(true);
  });

  it('schließt über „Abbrechen“ und das X', () => {
    const { fixture, el } = render();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    click(buttonByText(el, 'Abbrechen'));
    click(el.querySelector('button[aria-label="Schließen"]'));
    expect(closed).toHaveBeenCalledTimes(2);
  });
});
