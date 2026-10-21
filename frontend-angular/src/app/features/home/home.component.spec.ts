import { TestBed } from '@angular/core/testing';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  it('zeigt die deutsche Platzhalter-Startseite', async () => {
    await TestBed.configureTestingModule({ imports: [HomeComponent] }).compileComponents();
    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('Chronos');
    expect(el.textContent).toContain('Termine');
  });
});
