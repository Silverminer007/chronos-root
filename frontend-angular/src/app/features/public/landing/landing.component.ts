import { ChangeDetectionStrategy, Component } from '@angular/core';
import { demoAppointments } from './demo-appointments';
import { AppointmentDemoCardComponent } from './appointment-demo-card.component';
import { IconComponent } from '../../../shared/icon/icon.component';

interface Feature {
  icon: string;
  title: string;
  description: string;
}

/** Startseite `/` (Port von `pages/index.vue`). */
@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, AppointmentDemoCardComponent],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent {
  protected readonly demos = demoAppointments(new Date());

  protected readonly features: Feature[] = [
    {
      icon: 'calendar',
      title: 'Termine organisieren',
      description: 'Erstelle und verwalte alle Team-Termine an einem zentralen Ort. Nie wieder durcheinander.',
    },
    {
      icon: 'users',
      title: 'Verfügbarkeit abfragen',
      description: 'Sehe auf einen Blick, wer teilnehmen kann und ob genug Helfer dabei sind.',
    },
    {
      icon: 'bell',
      title: 'Push-Benachrichtigungen',
      description: 'Automatische Erinnerungen an wichtige Termine und laufende Abfragen.',
    },
    {
      icon: 'check-circle',
      title: 'Einfache Zusagen',
      description: 'Schnell und unkompliziert zu- oder absagen – in Sekunden erledigt.',
    },
  ];

  protected scrollToFeatures(): void {
    document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
  }
}
