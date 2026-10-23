import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OnboardingService } from '../../core/onboarding.service';
import { buttonByText, click } from '../appointment/testing';
import { OnboardingComponent } from './onboarding.component';

describe('OnboardingComponent', () => {
  let fixture: ComponentFixture<OnboardingComponent>;
  let navigate: ReturnType<typeof vi.fn>;
  const el = () => fixture.nativeElement as HTMLElement;
  const title = () => el().querySelector('h1')?.textContent;

  beforeEach(() => {
    localStorage.clear();
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: { navigateByUrl: navigate } }] });
    fixture = TestBed.createComponent(OnboardingComponent);
    fixture.detectChanges();
  });

  const press = (text: string) => {
    click(buttonByText(el(), text));
    fixture.detectChanges();
  };

  it('beginnt mit dem Willkommensschritt ohne „Zurück“', () => {
    expect(title()).toBe('Willkommen bei Chronos!');
    expect(buttonByText(el(), 'Zurück')).toBeUndefined();
    expect(buttonByText(el(), 'Überspringen')).toBeDefined();
  });

  it('blättert mit Weiter und Zurück durch die sechs Schritte', () => {
    press('Weiter');
    expect(title()).toBe('Termine verwalten');
    press('Zurück');
    expect(title()).toBe('Willkommen bei Chronos!');
    expect(el().querySelectorAll('.dot')).toHaveLength(6);
  });

  it('springt über die Punkte zu einem Schritt', () => {
    click(el().querySelectorAll('.dot')[3]);
    fixture.detectChanges();
    expect(title()).toBe('Teams & Gruppen');
  });

  it('zeigt im letzten Schritt „Loslegen“ statt „Weiter“ und „Überspringen“', () => {
    click(el().querySelectorAll('.dot')[5]);
    fixture.detectChanges();
    expect(title()).toBe('Immer informiert');
    expect(buttonByText(el(), 'Weiter')).toBeUndefined();
    expect(buttonByText(el(), 'Überspringen')).toBeUndefined();
    expect(buttonByText(el(), 'Loslegen')).toBeDefined();
  });

  it.each(['Überspringen', 'Loslegen'])('„%s“ schließt das Onboarding ab und öffnet die Agenda', (label) => {
    if (label === 'Loslegen') {
      click(el().querySelectorAll('.dot')[5]);
      fixture.detectChanges();
    }
    press(label);
    expect(TestBed.inject(OnboardingService).shouldShow()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/agenda');
  });
});
