import { TestBed } from '@angular/core/testing';
import { ModalService } from '../../core/modal.service';
import { Friend } from '../../core/models';
import { click } from '../appointment/testing';
import { FriendCardComponent } from './friend-card.component';

const friend = (overrides: Partial<Friend> = {}): Friend => ({
  user_id: 7,
  name: 'anna Beispiel',
  email: 'anna@x.de',
  profile_picture_url: '',
  friends_since: '2026-03-05T10:00:00',
  ...overrides,
});

describe('FriendCardComponent', () => {
  const modal = { confirm: vi.fn() };

  const render = (value: Friend) => {
    modal.confirm.mockReset().mockResolvedValue(true);
    TestBed.configureTestingModule({ providers: [{ provide: ModalService, useValue: modal }] });
    const fixture = TestBed.createComponent(FriendCardComponent);
    fixture.componentRef.setInput('friend', value);
    const removed: number[] = [];
    fixture.componentInstance.remove.subscribe((id) => removed.push(id));
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, removed };
  };

  it('zeigt Initiale, Name, E-Mail und „Freunde seit“', () => {
    const { el } = render(friend());
    expect(el.querySelector('.avatar')?.textContent).toContain('A');
    expect(el.textContent).toContain('anna Beispiel');
    expect(el.textContent).toContain('anna@x.de');
    expect(el.textContent).toContain('Freunde seit 05.03.2026');
  });

  it('blendet „Freunde seit“ ohne Datum aus und zeigt „?“ ohne Namen', () => {
    const { el } = render(friend({ friends_since: '', name: '' }));
    expect(el.textContent).not.toContain('Freunde seit');
    expect(el.querySelector('.avatar')?.textContent).toContain('?');
  });

  it('fragt vor dem Beenden nach und meldet die Freundes-ID', async () => {
    const { el, removed } = render(friend());
    click(el.querySelector('button'));
    await Promise.resolve();
    expect(modal.confirm).toHaveBeenCalledWith({
      title: 'Freundschaft beenden',
      message: 'Möchtest du die Freundschaft mit anna Beispiel wirklich beenden?',
      confirmText: 'Beenden',
      confirmColor: 'red',
    });
    await vi.waitFor(() => expect(removed).toEqual([7]));
  });

  it('beendet nichts, wenn der Dialog abgebrochen wird', async () => {
    const { el, removed } = render(friend());
    modal.confirm.mockResolvedValue(false);
    click(el.querySelector('button'));
    await Promise.resolve();
    await Promise.resolve();
    expect(removed).toEqual([]);
  });
});
