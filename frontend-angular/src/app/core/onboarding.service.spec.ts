import { TestBed } from '@angular/core/testing';
import { OnboardingService } from './onboarding.service';

describe('OnboardingService', () => {
  beforeEach(() => localStorage.clear());

  it('zeigt das Onboarding, solange es nicht abgeschlossen wurde', () => {
    expect(TestBed.inject(OnboardingService).shouldShow()).toBe(true);
  });

  it('merkt sich den Abschluss in localStorage (Schlüssel onboardingShownAt)', () => {
    const service = TestBed.inject(OnboardingService);
    service.markShown();
    expect(localStorage.getItem('onboardingShownAt')).toMatch(/^\d+$/);
    expect(service.shouldShow()).toBe(false);
  });
});
