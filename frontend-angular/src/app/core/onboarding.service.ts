import { Injectable } from '@angular/core';

const SHOWN_AT_STORAGE_KEY = 'onboardingShownAt';

/** Merkt, ob das Onboarding bereits abgeschlossen wurde (Port von `useOnboarding`). */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  shouldShow(): boolean {
    return !localStorage.getItem(SHOWN_AT_STORAGE_KEY);
  }

  markShown(): void {
    localStorage.setItem(SHOWN_AT_STORAGE_KEY, Date.now().toString());
  }
}
