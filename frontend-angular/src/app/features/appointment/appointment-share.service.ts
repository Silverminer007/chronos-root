import { Injectable, inject } from '@angular/core';
import { formatDateTime } from '../../core/date-format';
import { Appointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';

/** Teilen eines Termins per Web-Share-API oder Zwischenablage (Port von `useAppointmentShare`). */
@Injectable({ providedIn: 'root' })
export class AppointmentShareService {
  private readonly toast = inject(ToastService);

  buildShareText(appointment: Appointment, url: string): string {
    const count = (status: string) => appointment.participants.filter((p) => p.status === status).length;
    const lines = [`*${appointment.name}*`];
    if (appointment.description) lines.push(`Beschreibung: ${appointment.description}`);
    lines.push(`Start: ${formatDateTime(appointment.start)}`, `Ende: ${formatDateTime(appointment.end)}`);
    if (appointment.venue) lines.push(`Ort: ${appointment.venue}`);
    lines.push(
      '',
      '*Bisherige Rückmeldungen*:',
      `Zusagen: ${count('APPROVED')}`,
      `Absagen: ${count('REJECTED')}`,
      `Teilnehmer insgesamt: ${appointment.participants.length}`,
    );
    if (appointment.minimal_attendees) lines.push(`Mindest Teilnehmer: ${appointment.minimal_attendees}`);
    lines.push('', `Termindetails & Rückmeldung geben: ${url}`);
    return lines.join('\n');
  }

  async share(appointment: Appointment): Promise<void> {
    const text = this.buildShareText(appointment, `${window.location.origin}/appointment/${appointment.id}`);

    if (navigator.share) {
      try {
        await navigator.share({ title: appointment.name, text });
      } catch (err) {
        if ((err as DOMException).name !== 'AbortError') this.toast.error('Fehler', 'Teilen fehlgeschlagen');
      }
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      this.toast.success('Link kopiert', 'Der Termin-Link wurde in die Zwischenablage kopiert');
    } catch {
      this.toast.error('Fehler', 'Link konnte nicht kopiert werden');
    }
  }
}
