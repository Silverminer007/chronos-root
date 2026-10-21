import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { AuthService } from '../../../core/auth.service';
import { formatDate, formatTimeRange } from '../../../core/date-format';
import { Appointment } from '../../../core/models';
import { ToastService } from '../../../core/toast.service';
import { MessageDialogComponent } from '../../../shared/message-dialog.component';

const MAX_AVATARS = 4;
const SEND_DELAY_MS = 500;

/** Termin-Karte der Landingpage (Port von `AppointmentDemoCard.vue`); Zu- und Absagen ändern nur den lokalen Anzeigestand. */
@Component({
  selector: 'app-appointment-demo-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MessageDialogComponent],
  templateUrl: './appointment-demo-card.component.html',
  styleUrl: './appointment-demo-card.component.scss',
})
export class AppointmentDemoCardComponent {
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();

  protected readonly participants = linkedSignal(() => this.appointment().participants);
  protected readonly messageDialog = signal(false);

  protected readonly approved = computed(() => this.participants().filter((p) => p.status === 'APPROVED'));
  protected readonly avatars = computed(() => this.approved().slice(0, MAX_AVATARS));
  protected readonly hiddenAvatars = computed(() => this.approved().length - MAX_AVATARS);
  protected readonly hasApproved = computed(() => this.ownStatus() === 'APPROVED');
  protected readonly hasRejected = computed(() => this.ownStatus() === 'REJECTED');
  protected readonly timeRange = computed(() => formatTimeRange(this.appointment().start, this.appointment().end));
  protected readonly dialogTitle = computed(
    () => `${this.appointment().name} ${formatDate(this.appointment().start)}`,
  );

  private readonly ownStatus = computed(() => {
    const userId = this.auth.user()?.id;
    return this.participants().find((p) => p.user_id === userId)?.status;
  });

  protected setParticipationStatus(status: 'APPROVED' | 'REJECTED'): void {
    const userId = this.auth.user()?.id;
    this.participants.update((list) => list.map((p) => (p.user_id === userId ? { ...p, status } : p)));
  }

  protected async sendMessage(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, SEND_DELAY_MS));
    this.toast.info('Die Nachricht wurde versendet');
    this.messageDialog.set(false);
  }
}
