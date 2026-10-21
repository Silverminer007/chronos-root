import { ChangeDetectionStrategy, Component } from '@angular/core';
import { demoAppointments } from './demo-appointments';
import { AppointmentDemoCardComponent } from './appointment-demo-card.component';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

/** Startseite `/` (Port von `pages/index.vue`). */
@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AppointmentDemoCardComponent],
  templateUrl: './landing.component.html',
})
export class LandingComponent {
  protected readonly demos = demoAppointments(new Date());

  protected readonly features: Feature[] = [
    {
      icon: 'bi-calendar-event',
      title: 'Termine organisieren',
      description: 'Erstelle und verwalte alle Team-Termine an einem zentralen Ort. Nie wieder durcheinander.',
    },
    {
      icon: 'bi-people',
      title: 'Verfügbarkeit abfragen',
      description: 'Sehe auf einen Blick, wer teilnehmen kann und ob genug Helfer dabei sind.',
    },
    {
      icon: 'bi-bell',
      title: 'Push-Benachrichtigungen',
      description: 'Automatische Erinnerungen an wichtige Termine und laufende Abfragen.',
    },
    {
      icon: 'bi-check-circle',
      title: 'Einfache Zusagen',
      description: 'Schnell und unkompliziert zu- oder absagen – in Sekunden erledigt.',
    },
  ];

  protected scrollToFeatures(): void {
    document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
  }
}
