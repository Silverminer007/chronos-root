import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { getErrorMessage } from '../../core/errors';
import { Appointment } from '../../core/models';

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

  /** Lädt einen Termin neu und ersetzt ihn in der Liste; berührt `loading` nicht, damit die Liste nicht flackert. */
  async fetchAppointment(id: number): Promise<Appointment> {
    this.error.set(null);
    try {
      const appointment = await firstValueFrom(
        this.http.get<Appointment>(`${API}/${id}?participants=true&messages=true&group_participants=true`),
      );
      this.appointments.update((list) => list.map((a) => (a.id === id ? appointment : a)));
      return appointment;
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Laden des Appointments'));
      throw err;
    }
  }

  approveAppointment(id: number): Promise<void> {
    return this.setOwnParticipation(id, 'approve', 'Fehler beim Zusagen');
  }

  rejectAppointment(id: number): Promise<void> {
    return this.setOwnParticipation(id, 'reject', 'Fehler beim Absagen');
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

  private async setOwnParticipation(id: number, action: 'approve' | 'reject', fallback: string): Promise<void> {
    try {
      await firstValueFrom(this.http.post(`${API}/${id}/participants/${action}`, null));
      await this.fetchAppointment(id);
    } catch (err) {
      this.error.set(getErrorMessage(err, fallback));
      throw err;
    }
  }
}
