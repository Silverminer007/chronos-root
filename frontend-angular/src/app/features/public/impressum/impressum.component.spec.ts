import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PublicConfigService } from '../../../core/public-config.service';
import { ImpressumComponent } from './impressum.component';

describe('ImpressumComponent', () => {
  let http: HttpTestingController;

  async function render(impressum: Record<string, string> | null) {
    TestBed.configureTestingModule({ imports: [ImpressumComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ImpressumComponent);
    fixture.detectChanges();
    const req = http.expectOne('/api/config');
    if (impressum) req.flush({ impressum });
    else req.flush(null, { status: 502, statusText: 'Bad Gateway' });
    await TestBed.inject(PublicConfigService).loadImpressum();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('zeigt Überschrift und die konfigurierten Angaben', async () => {
    const el = await render({ name: 'Max Muster', street: 'Weg 1', city: '12345 Stadt', email: 'a@b.de', phone: '0123 456' });
    expect(el.querySelector('h1')?.textContent).toContain('Impressum');
    expect(el.textContent).toContain('Angaben gemäß § 5 TMG');
    expect(el.textContent).toContain('Max Muster');
    expect(el.textContent).toContain('Weg 1');
    expect(el.textContent).toContain('12345 Stadt');
    expect(el.querySelector('a[href="mailto:a@b.de"]')?.textContent?.trim()).toBe('a@b.de');
    expect(el.querySelector('a[href="tel:0123 456"]')?.textContent?.trim()).toBe('0123 456');
  });

  it('zeigt Platzhalter für nicht konfigurierte Werte', async () => {
    const el = await render({ name: '', street: '', city: '', email: '', phone: '' });
    expect(el.textContent).toContain('[Name nicht konfiguriert]');
    expect(el.textContent).toContain('[Straße nicht konfiguriert]');
    expect(el.textContent).toContain('[Stadt nicht konfiguriert]');
    expect(el.textContent).toContain('[E-Mail nicht konfiguriert]');
    expect(el.textContent).not.toContain('Telefon:');
  });

  it('zeigt Platzhalter, wenn die Konfiguration nicht geladen werden kann', async () => {
    const el = await render(null);
    expect(el.textContent).toContain('[Name nicht konfiguriert]');
  });

  it('enthält den Haftungsausschluss', async () => {
    const el = await render({ name: '', street: '', city: '', email: '', phone: '' });
    const h3 = Array.from(el.querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(h3).toEqual(['Haftung für Inhalte', 'Haftung für Links', 'Urheberrecht']);
  });
});
