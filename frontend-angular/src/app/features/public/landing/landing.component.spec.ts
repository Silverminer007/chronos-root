import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LandingComponent } from './landing.component';

describe('LandingComponent', () => {
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [LandingComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(LandingComponent);
    await fixture.whenStable();
    el = fixture.nativeElement as HTMLElement;
  });

  it('zeigt Hero mit Überschrift und Tagline', () => {
    expect(el.querySelector('h1')?.textContent).toContain('Terminplanung,');
    expect(el.querySelector('h1')?.textContent).toContain('die funktioniert');
    expect(el.textContent).toContain('Für Jugendverbände gemacht');
  });

  it('verlinkt Registrierung in Hero und Abschluss', () => {
    const links = Array.from(el.querySelectorAll('a[href="/api/auth/register"]')).map((a) => a.textContent?.trim());
    expect(links).toEqual(['Jetzt kostenlos starten', 'Kostenlos registrieren']);
  });

  it('zeigt die drei Demo-Karten', () => {
    expect(el.querySelectorAll('app-appointment-demo-card').length).toBe(3);
  });

  it('zeigt die vier Funktionskarten', () => {
    expect(el.querySelector('#features [role="heading"][aria-level="2"]')?.textContent).toContain('Alles, was dein Team braucht');
    const titles = Array.from(el.querySelectorAll('#features h3')).map((h) => h.textContent?.trim());
    expect(titles).toEqual([
      'Termine organisieren',
      'Verfügbarkeit abfragen',
      'Push-Benachrichtigungen',
      'Einfache Zusagen',
    ]);
  });

  it('zeigt den Abschlussbereich', () => {
    expect(Array.from(el.querySelectorAll('h2')).map((h) => h.textContent?.trim())).toContain('Bereit loszulegen?');
  });

  it('„Mehr erfahren“ scrollt zu den Funktionen', () => {
    const features = el.querySelector('#features') as HTMLElement;
    const scrollIntoView = vi.fn();
    features.scrollIntoView = scrollIntoView;
    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Mehr erfahren')) as HTMLElement).click();
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth' });
  });
});
