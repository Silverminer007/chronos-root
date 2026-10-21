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
      providers: [provideRouter([{ path: '', component: DefaultLayoutComponent, children: [{ path: '', component: DummyPage }] }])],
    });
    const harness = await RouterTestingHarness.create('/');
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('p')?.textContent).toBe('Seite');
    expect(el.querySelector('nav, header, footer')).toBeNull();
  });
});
