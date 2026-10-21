import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { AppointmentStore } from '../agenda/appointment.store';
import { AppointmentDetailsCardComponent } from './appointment-details-card.component';
import { AppointmentHeaderComponent } from './appointment-header.component';
import { AppointmentResponsibleActionsComponent } from './appointment-responsible-actions.component';
import { BackNavigation } from './back-navigation';
import { GroupParticipantsCardComponent } from './group-participants-card.component';
import { MessagesCardComponent } from './messages-card.component';
import { OwnParticipationStatusCardComponent } from './own-participation-status-card.component';
import { ParticipantsStatusCardComponent } from './participants-status-card.component';

/** Seite `/appointment/:id` (Port von `pages/appointment/[appointmentId].vue`). */
@Component({
  selector: 'app-appointment-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppointmentHeaderComponent,
    AppointmentDetailsCardComponent,
    OwnParticipationStatusCardComponent,
    ParticipantsStatusCardComponent,
    GroupParticipantsCardComponent,
    AppointmentResponsibleActionsComponent,
    MessagesCardComponent,
  ],
  templateUrl: './appointment-page.component.html',
  styleUrl: './appointment-page.component.scss',
})
export class AppointmentPageComponent {
  protected readonly store = inject(AppointmentStore);
  protected readonly backNavigation = inject(BackNavigation);

  protected readonly loading = signal(true);
  protected readonly error = signal('');

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed())
      .subscribe((params) => void this.load(Number(params.get('id'))));
    inject(DestroyRef).onDestroy(() => this.store.resetCurrentAppointment());
  }

  private async load(id: number): Promise<void> {
    this.store.resetCurrentAppointment();
    this.loading.set(true);
    this.error.set('');
    try {
      await this.store.fetchAppointment(id);
    } catch {
      this.error.set('Termin konnte nicht geladen werden');
    } finally {
      this.loading.set(false);
    }
  }
}
