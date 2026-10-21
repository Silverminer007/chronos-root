import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LandingLayoutComponent } from './landing-layout.component';

@Component({ template: '<h1>Inhalt</h1>' })
class DummyPage {}

describe('LandingLayoutComponent', () => {
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '', component: LandingLayoutComponent, children: [{ path: '', component: DummyPage }] }])],
    });
    const harness = await RouterTestingHarness.create('/');
    el = harness.routeNativeElement as HTMLElement;
  });

  it('rendert die Seite im Router-Outlet', () => {
    expect(el.querySelector('h1')?.textContent).toBe('Inhalt');
  });

  it('zeigt Logo-Link zur Startseite, Anmelden und Registrieren', () => {
    expect(el.querySelector('header a[href="/"]')?.textContent).toContain('Chronos');
    expect(el.querySelector('header a[href="/api/auth/login"]')?.textContent).toContain('Anmelden');
    expect(el.querySelector('header a[href="/api/auth/register"]')?.textContent).toContain('Registrieren');
  });

  it('enthält den Footer', () => {
    expect(el.querySelector('app-landing-footer')).not.toBeNull();
  });
});
