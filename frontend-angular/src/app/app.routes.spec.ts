import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('routes', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }),
  );

  const render = async (url: string) => (await RouterTestingHarness.create(url)).routeNativeElement as HTMLElement;

  it('lädt die Startseite im Landing-Layout', async () => {
    const el = await render('/');
    expect(el.querySelector('app-landing-layout app-landing h1')?.textContent).toContain('Terminplanung');
  });

  it.each([
    ['/public/install', 'app-install', 'Chronos installieren'],
    ['/public/impressum', 'app-impressum', 'Impressum'],
    ['/public/datenschutz', 'app-datenschutz', 'Datenschutzerklärung'],
  ])('lädt %s im Landing-Layout', async (url, selector, heading) => {
    const el = await render(url);
    expect(el.querySelector(`app-landing-layout ${selector} h1`)?.textContent).toContain(heading);
    expect(el.querySelector('app-landing-footer')).not.toBeNull();
  });

  it('lädt alle öffentlichen Seiten lazy', () => {
    const landing = routes.find((r) => r.component !== undefined || r.children);
    expect(landing?.children?.every((r) => typeof r.loadComponent === 'function')).toBe(true);
  });

  it('leitet unbekannte Pfade auf die Startseite um', async () => {
    await render('/gibt-es-nicht');
    expect(TestBed.inject(Router).url).toBe('/');
  });
});
