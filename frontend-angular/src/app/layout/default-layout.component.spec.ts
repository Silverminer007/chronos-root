import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { DefaultLayoutComponent } from './default-layout.component';

@Component({ template: '<p>Seite</p>' })
class DummyPage {}

describe('DefaultLayoutComponent', () => {
  it('ist ein Rahmen, der nur die Seite im Router-Outlet rendert (keine eigene Navigation)', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '', component: DefaultLayoutComponent, children: [{ path: '', component: DummyPage }] }])],
    });
    const harness = await RouterTestingHarness.create('/');
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('ng-component p')?.textContent).toBe('Seite');
    expect(el.querySelector('nav, header, footer')).toBeNull();
  });

  it('bindet PWA-Installation, Push-Berechtigung und Neuigkeiten über der Seite ein', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '', component: DefaultLayoutComponent, children: [{ path: '', component: DummyPage }] }])],
    });
    const harness = await RouterTestingHarness.create('/');
    const el = harness.routeNativeElement as HTMLElement;
    // Die Dialoge liegen in einem @defer-Block und erscheinen, sobald ihr Chunk geladen ist.
    await vi.waitFor(() => {
      harness.detectChanges();
      for (const tag of ['app-pwa-install-dialog', 'app-push-permission-modal', 'app-new-features-dialog']) {
        expect(el.querySelector(tag), tag).not.toBeNull();
      }
    });
  });
});
