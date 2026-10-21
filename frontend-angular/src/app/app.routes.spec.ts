import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('routes', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter(routes)] }));

  it('lädt die Startseite lazy', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect((harness.routeNativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('Chronos');
    expect(routes.find((r) => r.path === '')?.loadComponent).toBeTypeOf('function');
  });

  it('leitet unbekannte Pfade auf die Startseite um', async () => {
    await RouterTestingHarness.create('/gibt-es-nicht');
    expect(TestBed.inject(Router).url).toBe('/');
  });
});
