import { TestBed } from '@angular/core/testing';
import { Friend } from '../../core/models';
import { ParticipantLookupService } from '../appointment/participant-lookup.service';
import { FriendshipStore } from '../friends/friendship.store';
import { AppointmentParticipantPickerComponent } from './appointment-participant-picker.component';

const friend = (id: number, name: string): Friend => ({
  user_id: id,
  name,
  email: `${name}@x.de`,
  profile_picture_url: '',
  friends_since: '',
});

describe('AppointmentParticipantPickerComponent', () => {
  const lookup = { searchGroups: vi.fn() };
  const friendships = { fetchFriends: vi.fn() };

  const setup = () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: ParticipantLookupService, useValue: lookup },
        { provide: FriendshipStore, useValue: friendships },
      ],
    });
    const fixture = TestBed.createComponent(AppointmentParticipantPickerComponent);
    const changes: [number, number][] = [];
    fixture.componentInstance.changed.subscribe((c) =>
      changes.push([c.userCount, c.groupMemberCount]),
    );
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = async (text: string) => {
      const input = el.querySelector('input') as HTMLInputElement;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      await vi.advanceTimersByTimeAsync(250);
      fixture.detectChanges();
    };
    const tab = (label: string) => {
      (
        Array.from(el.querySelectorAll('[role="tab"]')).find((b) =>
          b.textContent?.includes(label),
        ) as HTMLElement
      ).click();
      fixture.detectChanges();
    };
    const results = () =>
      Array.from(el.querySelectorAll<HTMLButtonElement>('[data-testid="result"]'));
    return { fixture, el, type, tab, results, changes };
  };

  beforeEach(() => {
    vi.useFakeTimers();
    friendships.fetchFriends.mockReset().mockResolvedValue([friend(1, 'Anna'), friend(2, 'Anton')]);
    lookup.searchGroups
      .mockReset()
      .mockResolvedValue([{ id: 7, name: 'Jugend', members: [{}, {}, {}] }]);
  });
  afterEach(() => vi.useRealTimers());

  it('sucht erst ab zwei Zeichen und entprellt um 250 ms', async () => {
    const { el, type } = setup();
    await type('a');
    expect(friendships.fetchFriends).not.toHaveBeenCalled();
    const input = el.querySelector('input') as HTMLInputElement;
    input.value = 'an';
    input.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(249);
    expect(friendships.fetchFriends).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(friendships.fetchFriends).toHaveBeenCalledTimes(1);
  });

  it('filtert die Freunde lokal ohne Beachtung der Groß-/Kleinschreibung und lädt die Liste nur einmal', async () => {
    const { type, results } = setup();
    await type('AN');
    expect(results()).toHaveLength(2);
    await type('anto');
    expect(results()).toHaveLength(1);
    expect(friendships.fetchFriends).toHaveBeenCalledTimes(1);
  });

  it('zeigt Treffer und wählt eine Person mit der Rolle Teilnehmer', async () => {
    const { fixture, el, type, results, changes } = setup();
    await type('an');
    expect(results().map((r) => r.textContent)).toEqual([
      expect.stringContaining('Anna'),
      expect.stringContaining('Anton'),
    ]);
    results()[0].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedUsers()).toEqual([
      { id: 1, name: 'Anna', role: 'ATTENDANT' },
    ]);
    expect(el.querySelector('[data-testid="chip-user"]')?.textContent).toContain('Anna');
    expect(changes.at(-1)).toEqual([1, 0]);
  });

  it('wählt dieselbe Person nicht doppelt', async () => {
    const { fixture, type, results } = setup();
    await type('an');
    results()[0].click();
    await type('an');
    expect(results()[0].disabled).toBe(true);
    results()[0].click();
    expect(fixture.componentInstance.selectedUsers()).toHaveLength(1);
  });

  it('wechselt die Rolle zyklisch TN → Org → Helf → Gast → TN', async () => {
    const { fixture, el, type, results } = setup();
    await type('an');
    results()[0].click();
    fixture.detectChanges();
    const roleButton = () =>
      el.querySelector<HTMLButtonElement>('[data-testid="chip-user"] [data-testid="role"]')!;
    const seen: string[] = [roleButton().textContent!.trim()];
    for (let i = 0; i < 4; i++) {
      roleButton().click();
      fixture.detectChanges();
      seen.push(roleButton().textContent!.trim());
    }
    expect(seen).toEqual(['TN', 'Org', 'Helf', 'Gast', 'TN']);
    expect(fixture.componentInstance.selectedUsers()[0].role).toBe('ATTENDANT');
  });

  it('entfernt eine gewählte Person', async () => {
    const { fixture, el, type, results, changes } = setup();
    await type('an');
    results()[0].click();
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>(
      '[data-testid="chip-user"] [data-testid="remove"]',
    )!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedUsers()).toEqual([]);
    expect(changes.at(-1)).toEqual([0, 0]);
  });

  it('sucht im Gruppen-Tab Gruppen, zählt deren Mitglieder und leert beim Wechsel die Suche', async () => {
    const { fixture, el, type, tab, results, changes } = setup();
    await type('an');
    tab('Gruppen');
    expect((el.querySelector('input') as HTMLInputElement).value).toBe('');
    await type('ju');
    expect(lookup.searchGroups).toHaveBeenCalledWith('ju');
    expect(results()[0].textContent).toContain('3 Mitglieder');
    results()[0].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedGroups()).toEqual([
      { id: 7, name: 'Jugend', role: 'ATTENDANT', memberCount: 3 },
    ]);
    expect(changes.at(-1)).toEqual([0, 3]);
  });

  it('zeigt „Keine Ergebnisse“, wenn nichts gefunden wurde', async () => {
    friendships.fetchFriends.mockResolvedValue([]);
    const { el, type } = setup();
    await type('zz');
    expect(el.textContent).toContain('Keine Ergebnisse');
  });

  it('behandelt eine ungültige Gruppenantwort als leere Liste', async () => {
    lookup.searchGroups.mockResolvedValue(null);
    const { el, type, tab } = setup();
    tab('Gruppen');
    await type('zz');
    expect(el.textContent).toContain('Keine Ergebnisse');
  });
});
