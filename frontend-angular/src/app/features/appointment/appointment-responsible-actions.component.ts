import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { getErrorMessage } from '../../core/errors';
import { Appointment } from '../../core/models';
import { ModalService } from '../../core/modal.service';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { isResponsible } from './participation';

/** Aktionen für Organisierende: Termin absagen und löschen (Port von `AppointmentResponsibleActions.vue`). */
@Component({
  selector: 'app-appointment-responsible-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (responsible()) {
      <div class="card shadow-sm">
        <div class="card-body p-4">
          <h3 class="card-heading fw-bold mb-3">Organisator-Aktionen</h3>
          <button
            type="button"
            class="btn btn-outline-danger w-100 d-flex align-items-center justify-content-center gap-2"
            [disabled]="busy() || appointment().status === 'CANCELLED'"
            (click)="cancel()"
          >
            <i class="bi bi-x-circle" aria-hidden="true"></i><span>Termin absagen</span>
          </button>
          <button
            type="button"
            class="btn btn-outline-danger w-100 d-flex align-items-center justify-content-center gap-2 mt-2"
            [disabled]="busy()"
            (click)="remove()"
          >
            <i class="bi bi-trash" aria-hidden="true"></i><span>Termin löschen</span>
          </button>
        </div>
      </div>
    }
  `,
})
export class AppointmentResponsibleActionsComponent {
  private readonly auth = inject(AuthService);
  private readonly store = inject(AppointmentStore);
  private readonly modal = inject(ModalService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly appointment = input.required<Appointment>();
  protected readonly busy = signal(false);

  protected readonly responsible = computed(() =>
    isResponsible(this.appointment(), this.auth.user()?.id),
  );

  protected async cancel(): Promise<void> {
    const confirmed = await this.modal.confirm({
      title: 'Termin absagen',
      message: 'Möchtest du diesen Termin wirklich absagen? Alle Teilnehmer werden benachrichtigt.',
      confirmText: 'Termin absagen',
      confirmColor: 'red',
    });
    if (!confirmed) return;
    this.busy.set(true);
    try {
      await this.store.cancelAppointment(this.appointment().id);
      this.toast.success('Termin abgesagt', 'Alle Teilnehmer wurden benachrichtigt');
    } catch {
      this.toast.error('Fehler', 'Termin konnte nicht abgesagt werden');
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(): Promise<void> {
    const confirmed = await this.modal.confirm({
      title: 'Termin löschen',
      message: 'Termin endgültig löschen? Das kann nicht rückgängig gemacht werden.',
      confirmText: 'Termin löschen',
      confirmColor: 'red',
    });
    if (!confirmed) return;
    this.busy.set(true);
    try {
      await this.store.deleteAppointment(this.appointment().id);
      this.toast.success('Termin gelöscht');
      await this.router.navigateByUrl('/agenda');
    } catch (err) {
      this.toast.error('Fehler', getErrorMessage(err, 'Termin konnte nicht gelöscht werden'));
    } finally {
      this.busy.set(false);
    }
  }
}
