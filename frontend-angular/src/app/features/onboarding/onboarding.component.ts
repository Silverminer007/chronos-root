import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OnboardingService } from '../../core/onboarding.service';

interface Step {
  icon: string;
  title: string;
  description: string;
}

const STEPS: Step[] = [
  {
    icon: 'party-popper',
    title: 'Willkommen bei Chronos!',
    description: 'Schön, dass du dabei bist. Lass uns kurz zeigen, was Chronos für dich und dein Team kann.',
  },
  {
    icon: 'bi-calendar3',
    title: 'Termine verwalten',
    description: 'Erstelle Termine, lade Mitglieder ein und behalte den Überblick über alle anstehenden Events.',
  },
  {
    icon: 'bi-person',
    title: 'Freunde & Familie',
    description: 'Lade Freunde und Familie ein, um sie zu Terminen hinzuzufügen.',
  },
  {
    icon: 'bi-people',
    title: 'Teams & Gruppen',
    description: 'Erstelle Gruppen, um mehrere Freunde gleichzeitig zu einem Termin hinzuzufügen',
  },
  {
    icon: 'bi-people',
    title: 'Rollen & Verantwortlichkeiten',
    description: 'Weise Teilnehmenden eine Rolle für den Termin zu - von Gast bis Organisator',
  },
  {
    icon: 'bi-bell',
    title: 'Immer informiert',
    description: 'Erhalte Push-Benachrichtigungen bei neuen Terminen, Änderungen und Zu- oder Absagen.',
  },
];

/** Seite `/onboarding` ohne Layout: sechs Einführungsschritte (Port von `pages/onboarding.vue`). */
@Component({
  selector: 'app-onboarding',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './onboarding.component.html',
  styleUrl: './onboarding.component.scss',
})
export class OnboardingComponent {
  private readonly onboarding = inject(OnboardingService);
  private readonly router = inject(Router);

  protected readonly steps = STEPS;
  protected readonly currentStep = signal(0);
  protected readonly step = computed(() => STEPS[this.currentStep()]);
  protected readonly isLast = computed(() => this.currentStep() === STEPS.length - 1);

  protected previous(): void {
    this.currentStep.update((index) => index - 1);
  }

  protected next(): void {
    this.currentStep.update((index) => index + 1);
  }

  protected complete(): void {
    this.onboarding.markShown();
    void this.router.navigateByUrl('/agenda');
  }
}
