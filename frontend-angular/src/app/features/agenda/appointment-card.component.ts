import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { formatTimeRange } from '../../core/date-format';
import { Appointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from './appointment.store';

const MAX_AVATARS = 4;

/** Termin-Karte der Agenda mit Zu-/Absage (Port von `AppointmentCard.vue` ohne Nachrichten-Dialog, siehe Ticket #181). */
@Component({
  selector: 'app-appointment-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './appointment-card.component.html',
  styleUrl: './appointment-card.component.scss',
})
export class AppointmentCardComponent {
  private readonly store = inject(AppointmentStore);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();

  protected readonly approved = computed(() => this.appointment().participants.filter((p) => p.status === 'APPROVED'));
  protected readonly avatars = computed(() => this.approved().slice(0, MAX_AVATARS));
  protected readonly hiddenAvatars = computed(() => this.approved().length - MAX_AVATARS);
  protected readonly hasApproved = computed(() => this.ownStatus() === 'APPROVED');
  protected readonly hasRejected = computed(() => this.ownStatus() === 'REJECTED');
  protected readonly timeRange = computed(() => formatTimeRange(this.appointment().start, this.appointment().end));

  private readonly ownStatus = computed(() => {
    const userId = this.auth.user()?.id;
    return this.appointment().participants.find((p) => p.user_id === userId)?.status;
  });

  protected approve(): Promise<void> {
    return this.respond(() => this.store.approveAppointment(this.appointment().id), 'Zusage bestätigt');
  }

  protected reject(): Promise<void> {
    return this.respond(() => this.store.rejectAppointment(this.appointment().id), 'Absage registriert');
  }

  private async respond(action: () => Promise<void>, successSummary: string): Promise<void> {
    if (!this.auth.user()) return;
    try {
      await action();
      this.toast.success(successSummary);
    } catch (err) {
      console.error(err);
      this.toast.error('Fehler', 'Teilnahmestatus konnte nicht aktualisiert werden');
    }
  }
}
