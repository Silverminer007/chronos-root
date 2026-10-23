import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { User } from '../../core/models';
import { AppointmentStore } from './appointment.store';
import { SearchHeaderComponent } from './search-header.component';

describe('SearchHeaderComponent', () => {
  let fixture: ComponentFixture<SearchHeaderComponent>;
  let store: { search: ReturnType<typeof vi.fn> };
  let auth: { user: ReturnType<typeof signal<User | null>>; logout: ReturnType<typeof vi.fn> };
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    store = { search: vi.fn().mockResolvedValue([]) };
    auth = {
      user: signal<User | null>({ id: 1, first_name: 'Max', last_name: 'Muster', email: 'max@example.org' }),
      logout: vi.fn().mockResolvedValue(undefined),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AppointmentStore, useValue: store },
        { provide: AuthService, useValue: auth },
      ],
    });
    fixture = TestBed.createComponent(SearchHeaderComponent);
    fixture.detectChanges();
  });

  const typeQuery = (value: string) => {
    const input = el().querySelector('input[type="search"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const toggle = () => (el().querySelector('button[aria-haspopup]') as HTMLButtonElement).click();

  it('hat ein Suchfeld mit deutschem Platzhalter', () => {
    const input = el().querySelector('input[type="search"]') as HTMLInputElement;
    expect(input.placeholder).toBe('Termine durchsuchen...');
  });

  it('sucht bei jeder Eingabe im Store', () => {
    typeQuery('Zelt');
    expect(store.search).toHaveBeenCalledWith('Zelt');
  });

  it('zeigt Initialen und Vornamen des Benutzers', () => {
    expect(el().querySelector('button[aria-haspopup]')?.textContent).toContain('MM');
    expect(el().querySelector('button[aria-haspopup]')?.textContent).toContain('Max');
  });

  it('öffnet und schließt das Benutzermenü', () => {
    expect(el().querySelector('.dropdown-menu.show')).toBeNull();
    toggle();
    fixture.detectChanges();
    expect(el().querySelector('.dropdown-menu.show')).not.toBeNull();
    expect(el().querySelector('.dropdown-menu')?.textContent).toContain('max@example.org');
    toggle();
    fixture.detectChanges();
    expect(el().querySelector('.dropdown-menu.show')).toBeNull();
  });

  it('schließt das Menü bei Klick außerhalb', () => {
    toggle();
    fixture.detectChanges();
    document.body.click();
    fixture.detectChanges();
    expect(el().querySelector('.dropdown-menu.show')).toBeNull();
  });

  it('verlinkt Profil, Freunde, Gruppen und Einstellungen', () => {
    toggle();
    fixture.detectChanges();
    const links = Array.from(el().querySelectorAll('.dropdown-menu a')).map((a) => [
      a.textContent?.trim(),
      a.getAttribute('href'),
    ]);
    expect(links).toEqual([
      ['Profil', '/profile'],
      ['Freunde', '/friends'],
      ['Gruppen', '/groups'],
      ['Einstellungen', '/settings'],
    ]);
  });

  it('meldet über „Abmelden“ ab', () => {
    toggle();
    fixture.detectChanges();
    const logout = Array.from(el().querySelectorAll('.dropdown-menu button')).find((b) =>
      b.textContent?.includes('Abmelden'),
    ) as HTMLButtonElement;
    logout.click();
    expect(auth.logout).toHaveBeenCalled();
  });

  it('zeigt ohne Benutzer den Anmelden-Link statt des Menüs', () => {
    auth.user.set(null);
    fixture.detectChanges();
    expect(el().querySelector('button[aria-haspopup]')).toBeNull();
    expect(el().querySelector('a[href="/api/auth/login"]')?.textContent).toContain('Anmelden');
  });

  it('verlinkt das Logo auf die Agenda', () => {
    expect(el().querySelector('a[href="/agenda"]')?.textContent).toContain('Chronos');
  });
});
