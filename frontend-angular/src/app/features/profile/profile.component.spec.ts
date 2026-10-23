import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../../core/auth.service';
import { LinkedAccount, Passkey, User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { buttonByText, click } from '../appointment/testing';
import { ProfileComponent } from './profile.component';

const user: User = { id: 1, first_name: 'Max', last_name: 'Muster', email: 'max@x.de' };
const passkey = (overrides: Partial<Passkey> = {}): Passkey => ({
  id: 'pk1',
  name: 'Laptop',
  created_at: '2026-03-05T10:00:00Z',
  last_used_at: null,
  ...overrides,
});

describe('ProfileComponent', () => {
  let fixture: ComponentFixture<ProfileComponent>;
  let toast: ToastService;
  const auth = {
    user: () => user,
    fetchLinkedAccounts: vi.fn(),
    fetchPasskeys: vi.fn(),
    updateProfile: vi.fn(),
    unlinkAccount: vi.fn(),
    linkAccount: vi.fn(),
    changePassword: vi.fn(),
    createPasskey: vi.fn(),
    deletePasskey: vi.fn(),
    redirectTo: vi.fn(),
  };
  const el = () => fixture.nativeElement as HTMLElement;
  const input = (id: string) => el().querySelector(`#${id}`) as HTMLInputElement;

  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const create = async (linked: LinkedAccount[] = [], passkeys: Passkey[] = []) => {
    auth.fetchLinkedAccounts.mockResolvedValue(linked);
    auth.fetchPasskeys.mockResolvedValue(passkeys);
    fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    await settle();
  };

  beforeEach(() => {
    Object.values(auth).forEach((fn) => typeof fn === 'function' && 'mockReset' in fn && fn.mockReset());
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: auth }],
    });
    toast = TestBed.inject(ToastService);
  });

  it('zeigt Initialen, Namen und E-Mail des Nutzers in Anzeige und Formular', async () => {
    await create();
    expect(el().textContent).toContain('MM');
    expect(el().textContent).toContain('Max Muster');
    expect(el().textContent).toContain('max@x.de');
    expect(input('profile-first-name').value).toBe('Max');
    expect(input('profile-last-name').value).toBe('Muster');
    expect(input('profile-email').value).toBe('max@x.de');
  });

  it('speichert das Profil mit den Formularwerten', async () => {
    await create();
    input('profile-first-name').value = 'Erika';
    input('profile-first-name').dispatchEvent(new Event('input'));
    auth.updateProfile.mockResolvedValue(undefined);
    click(buttonByText(el(), 'Speichern'));
    await settle();
    expect(auth.updateProfile).toHaveBeenCalledWith({ first_name: 'Erika', last_name: 'Muster', email: 'max@x.de' });
    expect(toast.toasts().at(-1)).toMatchObject({ severity: 'success', summary: 'Profil gespeichert' });
    expect(auth.redirectTo).not.toHaveBeenCalled();
    expect(el().textContent).toContain('Erika Muster');
  });

  it('wechselt zur E-Mail-Verifizierung, wenn Keycloak eine URL liefert', async () => {
    await create();
    auth.updateProfile.mockResolvedValue('https://kc/verify');
    click(buttonByText(el(), 'Speichern'));
    await settle();
    expect(auth.redirectTo).toHaveBeenCalledWith('https://kc/verify');
  });

  it('meldet einen Speicherfehler', async () => {
    await create();
    auth.updateProfile.mockRejectedValue(new Error('x'));
    click(buttonByText(el(), 'Speichern'));
    await settle();
    expect(toast.toasts().at(-1)).toMatchObject({ severity: 'error', summary: 'Fehler beim Speichern', detail: 'Bitte versuche es erneut' });
    expect(buttonByText(el(), 'Speichern')?.disabled).toBe(false);
  });

  it('leitet zum Passwortwechsel weiter', async () => {
    await create();
    auth.changePassword.mockResolvedValue('https://kc/pw');
    click(buttonByText(el(), 'Passwort ändern'));
    await settle();
    expect(auth.redirectTo).toHaveBeenCalledWith('https://kc/pw');
  });

  it('meldet, wenn die Weiterleitung nicht vorbereitet werden kann', async () => {
    await create();
    auth.changePassword.mockRejectedValue(new Error('x'));
    click(buttonByText(el(), 'Passwort ändern'));
    await settle();
    expect(auth.redirectTo).not.toHaveBeenCalled();
    expect(toast.toasts().at(-1)).toMatchObject({ severity: 'error', summary: 'Fehler' });
  });

  describe('Verknüpfte Konten', () => {
    it('bietet Verknüpfen an, wenn Google nicht verknüpft ist', async () => {
      await create();
      expect(el().textContent).toContain('Nicht verknüpft');
      auth.linkAccount.mockResolvedValue('https://kc/link');
      click(buttonByText(el(), 'Verknüpfen'));
      await settle();
      expect(auth.linkAccount).toHaveBeenCalledWith('google');
      expect(auth.redirectTo).toHaveBeenCalledWith('https://kc/link');
    });

    it('trennt ein verknüpftes Konto', async () => {
      await create([{ provider: 'google' }]);
      expect(el().textContent).toContain('Verknüpft');
      auth.unlinkAccount.mockResolvedValue(undefined);
      click(buttonByText(el(), 'Trennen'));
      await settle();
      expect(auth.unlinkAccount).toHaveBeenCalledWith('google');
      expect(toast.toasts().at(-1)).toMatchObject({ severity: 'success', summary: 'Konto getrennt' });
      expect(el().textContent).toContain('Nicht verknüpft');
    });

    it('meldet einen Fehler beim Trennen und behält das Konto', async () => {
      await create([{ provider: 'google' }]);
      auth.unlinkAccount.mockRejectedValue(new Error('x'));
      click(buttonByText(el(), 'Trennen'));
      await settle();
      expect(toast.toasts().at(-1)).toMatchObject({ severity: 'error', detail: 'Konto konnte nicht getrennt werden' });
      expect(buttonByText(el(), 'Trennen')).toBeDefined();
    });

    it('lässt den Abschnitt bei einem Ladefehler als „nicht verknüpft“ stehen', async () => {
      auth.fetchLinkedAccounts.mockRejectedValue(new Error('x'));
      auth.fetchPasskeys.mockResolvedValue([]);
      fixture = TestBed.createComponent(ProfileComponent);
      fixture.detectChanges();
      await settle();
      expect(el().textContent).toContain('Nicht verknüpft');
    });
  });

  describe('Passkeys', () => {
    it('zeigt den Leerzustand', async () => {
      await create();
      expect(el().textContent).toContain('Keine Passkeys hinterlegt');
    });

    it('listet Passkeys mit Erstell- und Nutzungsdatum', async () => {
      await create([], [passkey({ last_used_at: '2026-04-06T10:00:00Z' }), passkey({ id: 'pk2', name: 'Handy' })]);
      expect(el().textContent).toContain('Laptop');
      expect(el().textContent).toContain('Erstellt am 05.03.2026');
      expect(el().textContent).toContain('Zuletzt genutzt 06.04.2026');
      expect(el().textContent).toContain('Handy');
      expect(el().textContent?.match(/Zuletzt genutzt/g)).toHaveLength(1);
    });

    it('leitet zum Hinzufügen weiter', async () => {
      await create();
      auth.createPasskey.mockResolvedValue('https://kc/pk');
      click(buttonByText(el(), 'Hinzufügen'));
      await settle();
      expect(auth.redirectTo).toHaveBeenCalledWith('https://kc/pk');
    });

    it('leitet zum Entfernen weiter', async () => {
      await create([], [passkey()]);
      auth.deletePasskey.mockResolvedValue('https://kc/del');
      click(buttonByText(el(), 'Entfernen'));
      await settle();
      expect(auth.deletePasskey).toHaveBeenCalledWith('pk1');
      expect(auth.redirectTo).toHaveBeenCalledWith('https://kc/del');
    });
  });
});
