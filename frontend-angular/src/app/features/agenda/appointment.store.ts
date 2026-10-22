import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { getErrorMessage } from '../../core/errors';
import { Appointment, Role } from '../../core/models';
import { AppointmentPayload } from '../appointment-form/appointment-form';

const API = '/api/v2/appointments';
const PAGE_SIZE = 20;

interface PaginatedAppointments {
  data: Appointment[];
  meta: { page: number; size: number; total: number };
}

export interface Pagination {
  page: number;
  size: number;
  total: number;
}

export interface AppointmentFilters {
  start?: string;
  end?: string;
  search?: string;
  page?: number;
  size?: number;
  participants?: boolean;
  messages?: boolean;
  groups?: boolean;
}

/** Terminliste mit Paginierung und Suche (Port des Lese-Teils von `stores/appointments.ts`); Zustand als Signals. */
@Injectable({ providedIn: 'root' })
export class AppointmentStore {
  private readonly http = inject(HttpClient);

  readonly appointments = signal<Appointment[]>([]);
  /** Termin der Detailseite; wird von `fetchAppointment` und damit nach jeder Mutation aktualisiert. */
  readonly currentAppointment = signal<Appointment | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly pagination = signal<Pagination>({ page: 0, size: PAGE_SIZE, total: 0 });
  readonly hasMore = computed(() => {
    const { page, size, total } = this.pagination();
    return (page + 1) * size < total;
  });

  private searchQuery: string | null = null;
  private latestRequest = 0;

  /** Lädt eine Seite der Agenda; Seite 0 (oder ohne Seite) ersetzt die Liste, sonst wird angehängt. */
  async fetchAgenda(filters: AppointmentFilters = {}): Promise<Appointment[]> {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.error.set(null);

    try {
      const params = new URLSearchParams();
      if (filters.start) params.append('start', filters.start);
      if (filters.end) params.append('end', filters.end);
      if (filters.search) params.append('search', filters.search);
      if (filters.page !== undefined) {
        params.append('page', String(filters.page));
        this.pagination.update((p) => ({ ...p, page: filters.page as number }));
      }
      if (filters.size !== undefined) {
        params.append('size', String(filters.size));
        this.pagination.update((p) => ({ ...p, size: filters.size as number }));
      }
      if (filters.participants) params.append('participants', 'true');
      if (filters.messages) params.append('messages', 'true');
      if (filters.groups) params.append('groups', 'true');

      const response = await firstValueFrom(
        this.http.get<PaginatedAppointments | Appointment[]>(`${API}?${params.toString()}`),
      );
      // Eine neuere Anfrage (z. B. weitergetippte Suche) hat die Liste übernommen: späte Antwort verwerfen.
      if (request !== this.latestRequest) return this.appointments();

      const items = Array.isArray(response) ? response : response.data;
      if (!Array.isArray(response)) {
        this.pagination.set({ ...response.meta });
      }
      const keep = filters.page ? this.appointments() : [];
      this.appointments.set(keep.concat(items));
    } catch (err) {
      if (request === this.latestRequest) {
        this.error.set(getErrorMessage(err, 'Fehler beim Laden der Appointments'));
      }
      throw err;
    } finally {
      if (request === this.latestRequest) this.loading.set(false);
    }
    return this.appointments();
  }

  /** Lädt einen Termin neu, setzt ihn als aktuellen Termin und ersetzt ihn in der Liste; berührt `loading` nicht, damit die Liste nicht flackert. */
  async fetchAppointment(id: number): Promise<Appointment> {
    this.error.set(null);
    try {
      const appointment = await firstValueFrom(
        this.http.get<Appointment>(
          `${API}/${id}?participants=true&messages=true&group_participants=true`,
        ),
      );
      this.currentAppointment.set(appointment);
      this.appointments.update((list) => list.map((a) => (a.id === id ? appointment : a)));
      return appointment;
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Laden des Appointments'));
      throw err;
    }
  }

  /** Legt einen Termin an. Berührt `loading` nicht (die Aufrufer zeigen ihren eigenen Speicherzustand). */
  async createAppointment(data: AppointmentPayload): Promise<Appointment> {
    this.error.set(null);
    try {
      const created = await firstValueFrom(this.http.post<Appointment>(API, data));
      this.appointments.update((list) => [...list, created]);
      return created;
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Erstellen des Appointments'));
      throw err;
    }
  }

  async updateAppointment(id: number, data: Partial<Appointment>): Promise<Appointment> {
    this.error.set(null);
    try {
      const updated = await firstValueFrom(this.http.patch<Appointment>(`${API}/${id}`, data));
      this.appointments.update((list) => list.map((a) => (a.id === id ? updated : a)));
      if (this.currentAppointment()?.id === id) this.currentAppointment.set(updated);
      return updated;
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Aktualisieren des Appointments'));
      throw err;
    }
  }

  /** Verschiebt einen Termin auf einen neuen Zeitraum (PATCH von `start` und `end`). */
  moveAppointment(id: number, start: string, end: string): Promise<Appointment> {
    return this.updateAppointment(id, { start, end });
  }

  async deleteAppointment(id: number): Promise<void> {
    this.error.set(null);
    try {
      await firstValueFrom(this.http.delete(`${API}/${id}`));
      this.appointments.update((list) => list.filter((a) => a.id !== id));
      if (this.currentAppointment()?.id === id) this.currentAppointment.set(null);
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Löschen des Appointments'));
      throw err;
    }
  }

  cancelAppointment(id: number): Promise<void> {
    return this.mutate(id, 'POST', 'cancel', null, 'Fehler beim Absagen des Appointments');
  }

  approveAppointment(id: number): Promise<void> {
    return this.setOwnParticipation(id, 'APPROVED', 'Fehler beim Zusagen');
  }

  rejectAppointment(id: number): Promise<void> {
    return this.setOwnParticipation(id, 'REJECTED', 'Fehler beim Absagen');
  }

  addParticipant(id: number, userId: number, role: Role): Promise<void> {
    return this.mutate(
      id,
      'POST',
      `participants/${userId}`,
      { user_role: role },
      'Fehler beim Hinzufügen des Teilnehmers',
    );
  }

  removeParticipant(id: number, userId: number): Promise<void> {
    return this.mutate(
      id,
      'DELETE',
      `participants/${userId}`,
      null,
      'Fehler beim Entfernen des Teilnehmers',
    );
  }

  changeParticipantRole(id: number, userId: number, role: Role): Promise<void> {
    return this.mutate(
      id,
      'PUT',
      `participation/${userId}/role`,
      { role },
      'Fehler beim Ändern der Rolle',
    );
  }

  addGroupParticipant(id: number, groupId: number, role: Role): Promise<void> {
    return this.mutate(
      id,
      'POST',
      `groups/${groupId}`,
      { user_role: role },
      'Fehler beim Hinzufügen der Gruppe',
    );
  }

  removeGroupParticipant(id: number, groupId: number): Promise<void> {
    return this.mutate(
      id,
      'DELETE',
      `groups/${groupId}`,
      null,
      'Fehler beim Entfernen der Gruppe',
    );
  }

  sendMessage(id: number, body: string): Promise<void> {
    return this.mutate(id, 'POST', 'messages', { body }, 'Fehler beim Senden der Nachricht');
  }

  resetCurrentAppointment(): void {
    this.currentAppointment.set(null);
    this.error.set(null);
  }

  /** Erste Seite der Agenda (ohne Suche). */
  loadInitialAppointments(): Promise<Appointment[]> {
    this.searchQuery = null;
    return this.fetchAgenda({ participants: true, page: 0, size: PAGE_SIZE });
  }

  /** Nächste Seite („Mehr laden“); behält den aktiven Suchbegriff. */
  loadMore(): Promise<Appointment[]> {
    const { page, size } = this.pagination();
    return this.fetchAgenda({
      search: this.searchQuery || undefined,
      participants: true,
      page: page + 1,
      size,
    });
  }

  search(query: string): Promise<Appointment[]> {
    this.searchQuery = query || null;
    this.appointments.set([]);
    return this.fetchAgenda({ search: query, participants: true, page: 0, size: PAGE_SIZE });
  }

  private setOwnParticipation(
    id: number,
    status: 'APPROVED' | 'REJECTED',
    fallback: string,
  ): Promise<void> {
    return this.mutate(id, 'POST', 'participation', { status }, fallback);
  }

  /** Sendet die Änderung und lädt den Termin neu; Fehler setzen `error()` und werden weitergeworfen. */
  private async mutate(
    id: number,
    method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
    path: string,
    body: unknown,
    fallback: string,
  ): Promise<void> {
    try {
      await firstValueFrom(this.http.request(method, `${API}/${id}/${path}`, { body }));
      await this.fetchAppointment(id);
    } catch (err) {
      this.error.set(getErrorMessage(err, fallback));
      throw err;
    }
  }
}
