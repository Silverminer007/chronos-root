import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PublicConfigService } from '../../../core/public-config.service';
import { DatenschutzComponent } from './datenschutz.component';

describe('DatenschutzComponent', () => {
  async function render(impressum: Record<string, string>) {
    TestBed.configureTestingModule({ imports: [DatenschutzComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DatenschutzComponent);
    fixture.detectChanges();
    http.expectOne('/api/config').flush({ impressum });
    await TestBed.inject(PublicConfigService).loadImpressum();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const full = { name: 'Max Muster', street: 'Weg 1', city: '12345 Stadt', email: 'a@b.de', phone: '' };

  it('zeigt Überschrift und die zehn Abschnitte', async () => {
    const el = await render(full);
    expect(el.querySelector('h1')?.textContent).toContain('Datenschutzerklärung');
    const sections = Array.from(el.querySelectorAll('h2')).map((h) => h.textContent?.trim());
    expect(sections).toEqual([
      '1. Verantwortlicher',
      '2. Welche Daten wir erheben',
      '3. Cookies',
      '4. Zweck der Datenverarbeitung',
      '5. Rechtsgrundlage',
      '6. Datenweitergabe',
      '7. Speicherdauer',
      '8. Ihre Rechte',
      '9. Datensicherheit',
      '10. Änderungen dieser Datenschutzerklärung',
    ]);
  });

  it('zeigt den Verantwortlichen aus der Konfiguration mit E-Mail-Links', async () => {
    const el = await render(full);
    expect(el.textContent).toContain('Max Muster');
    expect(el.textContent).toContain('Weg 1');
    expect(el.querySelectorAll('a[href="mailto:a@b.de"]').length).toBe(2);
    expect(el.textContent).toContain('Zur Ausübung Ihrer Rechte kontaktieren Sie uns bitte unter:');
  });

  it('zeigt Platzhalter und keine Kontaktzeile ohne Konfiguration', async () => {
    const el = await render({ name: '', street: '', city: '', email: '', phone: '' });
    expect(el.textContent).toContain('[Name nicht konfiguriert]');
    expect(el.textContent).toContain('[Straße nicht konfiguriert]');
    expect(el.textContent).toContain('[Stadt nicht konfiguriert]');
    expect(el.querySelector('a[href^="mailto:"]')).toBeNull();
  });

  it('listet die Cookies', async () => {
    const el = await render(full);
    const cookies = Array.from(el.querySelectorAll('tbody tr')).map((r) => r.querySelector('td')?.textContent?.trim());
    expect(cookies).toEqual(['kc_access', 'kc_refresh']);
  });
});
