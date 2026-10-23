import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AppointmentHeaderComponent } from './appointment-header.component';
import { appointment, click } from './testing';

describe('AppointmentHeaderComponent', () => {
  const setup = (navigationId: number) => {
    const location = { back: vi.fn() };
    const router = { navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
      ],
    });
    vi.spyOn(history, 'state', 'get').mockReturnValue({ navigationId });
    const fixture = TestBed.createComponent(AppointmentHeaderComponent);
    fixture.componentRef.setInput('appointment', appointment());
    fixture.detectChanges();
    return { fixture, location, router };
  };

  afterEach(() => vi.restoreAllMocks());

  it('zeigt den Terminnamen als Überschrift', () => {
    const { fixture } = setup(1);
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Zeltlager');
  });

  it('geht zurück, wenn es einen vorherigen Eintrag gibt', () => {
    const { fixture, location, router } = setup(2);
    click(fixture.nativeElement.querySelector('button[aria-label="Zurück"]'));
    expect(location.back).toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('wechselt ohne vorherigen Eintrag zur Agenda', () => {
    const { fixture, location, router } = setup(1);
    click(fixture.nativeElement.querySelector('button[aria-label="Zurück"]'));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/agenda');
    expect(location.back).not.toHaveBeenCalled();
  });
});
