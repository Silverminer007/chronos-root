import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CreateAppointmentStepperComponent } from '../appointment-form/create-appointment-stepper.component';
import { AppointmentCardSkeletonComponent } from './appointment-card-skeleton.component';
import { AppointmentCardComponent } from './appointment-card.component';
import { AppointmentStore } from './appointment.store';
import { SearchHeaderComponent } from './search-header.component';

/** Terminübersicht `/agenda` (Port von `pages/agenda.vue`) mit Anlegen-Button und Stepper. */
@Component({
  selector: 'app-agenda',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SearchHeaderComponent,
    AppointmentCardComponent,
    AppointmentCardSkeletonComponent,
    CreateAppointmentStepperComponent,
  ],
  templateUrl: './agenda.component.html',
  styleUrl: './agenda.component.scss',
})
export class AgendaComponent implements OnInit {
  protected readonly store = inject(AppointmentStore);
  private readonly router = inject(Router);

  protected readonly skeletons = [1, 2, 3, 4, 5];
  protected readonly stepperOpen = signal(false);

  ngOnInit(): void {
    // Fehler setzt der Store als Meldung, die Seite zeigt sie an.
    this.store.loadInitialAppointments().catch(() => undefined);
  }

  protected loadMore(): void {
    this.store.loadMore().catch(() => undefined);
  }

  protected onCreated(id: number): void {
    void this.router.navigateByUrl(`/appointment/${id}`);
  }
}
