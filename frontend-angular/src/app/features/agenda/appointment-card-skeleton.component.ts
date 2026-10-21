import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Platzhalter einer Termin-Karte während des Ladens (Port von `AppointmentCardSkeleton.vue`). */
@Component({
  selector: 'app-appointment-card-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './appointment-card-skeleton.component.html',
  styleUrl: './appointment-card-skeleton.component.scss',
})
export class AppointmentCardSkeletonComponent {
  protected readonly avatars = [1, 2, 3, 4];
}
