import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LandingFooterComponent } from './landing-footer.component';

describe('LandingFooterComponent', () => {
  it('zeigt Copyright und Links auf Impressum und Datenschutz', async () => {
    await TestBed.configureTestingModule({ imports: [LandingFooterComponent], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(LandingFooterComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain(`© ${new Date().getUTCFullYear()} Chronos. Terminplanung für Jugendverbände.`);
    const hrefs = Array.from(el.querySelectorAll('a')).map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(hrefs).toEqual([
      ['Impressum', '/public/impressum'],
      ['Datenschutz', '/public/datenschutz'],
    ]);
  });
});
