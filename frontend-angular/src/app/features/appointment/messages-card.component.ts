import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { formatDateTime } from '../../core/date-format';
import { Appointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { MessageDialogComponent } from '../../shared/message-dialog.component';
import { AppointmentStore } from '../agenda/appointment.store';

/** Nachrichtenverlauf eines Termins mit Senden-Dialog (Port von `MessagesCard.vue`). */
@Component({
  selector: 'app-messages-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MessageDialogComponent],
  template: `
    <div class="card shadow-sm">
      <div class="card-header p-4 bg-transparent d-flex flex-column flex-sm-row align-items-sm-center justify-content-between gap-3">
        <h3 class="h5 fw-bold mb-0">Nachrichten</h3>
        <button type="button" class="btn btn-gradient d-flex align-items-center justify-content-center gap-2" (click)="showDialog.set(true)">
          <i class="bi bi-send" aria-hidden="true"></i><span>Nachricht senden</span>
        </button>
      </div>

      <ul class="list-group list-group-flush">
        @for (message of messages(); track message.id) {
          <li class="list-group-item p-4 d-flex gap-3" data-testid="message">
            <span class="avatar flex-shrink-0" aria-hidden="true"><i class="bi bi-person"></i></span>
            <div class="min-w-0">
              <p class="fw-semibold mb-0" data-testid="sender">{{ message.sender_name }}</p>
              <p class="small text-body-secondary mb-2">{{ time(message.timestamp) }}</p>
              <p class="mb-0 text-pre-wrap">{{ message.body }}</p>
            </div>
          </li>
        } @empty {
          <li class="list-group-item p-5 text-center text-body-secondary">
            <i class="bi bi-inbox fs-1 d-block mb-3" aria-hidden="true"></i>
            <p class="mb-0">Noch keine Nachrichten</p>
          </li>
        }
      </ul>
    </div>

    <app-message-dialog
      [visible]="showDialog()"
      [appointmentTitle]="appointment().name"
      [recipientCount]="appointment().participants.length"
      (closed)="showDialog.set(false)"
      (sent)="send($event.message)"
    />
  `,
})
export class MessagesCardComponent {
  private readonly store = inject(AppointmentStore);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();

  protected readonly showDialog = signal(false);
  protected readonly messages = computed(() =>
    [...this.appointment().messages].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()),
  );
  protected readonly time = formatDateTime;

  protected async send(message: string): Promise<void> {
    try {
      await this.store.sendMessage(this.appointment().id, message);
      this.toast.success('Nachricht versendet');
      this.showDialog.set(false);
    } catch {
      this.toast.error('Fehler beim Senden', 'Bitte kontaktiere den Entwickler der App');
    }
  }
}
