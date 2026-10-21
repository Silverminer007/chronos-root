import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AppointmentCardSkeletonComponent } from './appointment-card-skeleton.component';
import { AppointmentCardComponent } from './appointment-card.component';
import { AppointmentStore } from './appointment.store';
import { SearchHeaderComponent } from './search-header.component';

/** Terminübersicht `/agenda` (Port von `pages/agenda.vue`; Anlegen-Button und Stepper folgen mit F-06). */
@Component({
  selector: 'app-agenda',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, AppointmentCardComponent, AppointmentCardSkeletonComponent],
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.scss',
})
export class AgendaComponent implements OnInit {
  protected readonly store = inject(AppointmentStore);
  protected readonly skeletons = [1, 2, 3, 4, 5];

  ngOnInit(): void {
    // Fehler setzt der Store als Meldung, die Seite zeigt sie an.
    this.store.loadInitialAppointments().catch(() => undefined);
  }

  protected loadMore(): void {
    this.store.loadMore().catch(() => undefined);
  }
}
