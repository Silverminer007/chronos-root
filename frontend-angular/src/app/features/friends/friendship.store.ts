import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { getErrorMessage } from '../../core/errors';
import { Friend, FriendshipRequest } from '../../core/models';

const API = '/api/v2/friendships';

/** Freunde und Freundschaftsanfragen (Port von `stores/friendships.ts`); Zustand als Signals. */
@Injectable({ providedIn: 'root' })
export class FriendshipStore {
  private readonly http = inject(HttpClient);

  readonly friends = signal<Friend[]>([]);
  readonly incomingRequests = signal<FriendshipRequest[]>([]);
  readonly outgoingRequests = signal<FriendshipRequest[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly friendsCount = computed(() => this.friends().length);
  readonly incomingRequestsCount = computed(() => this.incomingRequests().length);
  readonly outgoingRequestsCount = computed(() => this.outgoingRequests().length);
  readonly totalRequestsCount = computed(() => this.incomingRequestsCount() + this.outgoingRequestsCount());

  /** Lädt die Freunde; liefert die Liste auch den Teilnehmer-Suchen (Ersatz für `useFriends`). */
  async fetchFriends(): Promise<Friend[]> {
    const friends = await this.load(
      () => firstValueFrom(this.http.get<Friend[] | null>(`${API}/friends`)),
      'Fehler beim Laden der Freunde',
    );
    this.friends.set(friends ?? []);
    return this.friends();
  }

  async fetchIncomingRequests(): Promise<void> {
    const requests = await this.load(
      () => firstValueFrom(this.http.get<FriendshipRequest[] | null>(`${API}/requests/incoming`)),
      'Fehler beim Laden der eingehenden Anfragen',
    );
    this.incomingRequests.set(requests ?? []);
  }

  async fetchOutgoingRequests(): Promise<void> {
    const requests = await this.load(
      () => firstValueFrom(this.http.get<FriendshipRequest[] | null>(`${API}/requests/outgoing`)),
      'Fehler beim Laden der ausgehenden Anfragen',
    );
    this.outgoingRequests.set(requests ?? []);
  }

  async fetchAll(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await Promise.all([this.fetchFriends(), this.fetchIncomingRequests(), this.fetchOutgoingRequests()]);
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Laden der Daten'));
      throw err;
    } finally {
      this.loading.set(false);
    }
  }

  async endFriendship(friendId: number): Promise<void> {
    await this.mutate(() => firstValueFrom(this.http.delete(`${API}/friends/${friendId}`)), 'Fehler beim Beenden der Freundschaft');
    this.friends.update((list) => list.filter((f) => f.user_id !== friendId));
  }

  async sendRequest(email: string): Promise<void> {
    await this.mutate(
      () => firstValueFrom(this.http.post(`${API}/requests`, null, { params: { email } })),
      'Fehler beim Senden der Anfrage',
    );
    await this.fetchOutgoingRequests().catch(() => undefined); // Aktion gelang; Reload-Fehler steht in error()
  }

  async acceptRequest(requestId: number): Promise<void> {
    await this.mutate(
      () => firstValueFrom(this.http.post(`${API}/requests/${requestId}/accept`, null)),
      'Fehler beim Annehmen der Anfrage',
    );
    this.incomingRequests.update((list) => list.filter((r) => r.requestId !== requestId));
    await this.fetchFriends().catch(() => undefined); // Aktion gelang; Reload-Fehler steht in error()
  }

  async declineRequest(requestId: number): Promise<void> {
    await this.mutate(
      () => firstValueFrom(this.http.post(`${API}/requests/${requestId}/decline`, null)),
      'Fehler beim Ablehnen der Anfrage',
    );
    this.incomingRequests.update((list) => list.filter((r) => r.requestId !== requestId));
  }

  async cancelRequest(requestId: number): Promise<void> {
    await this.mutate(
      () => firstValueFrom(this.http.delete(`${API}/requests/${requestId}`)),
      'Fehler beim Zurückziehen der Anfrage',
    );
    this.outgoingRequests.update((list) => list.filter((r) => r.requestId !== requestId));
  }

  clearError(): void {
    this.error.set(null);
  }

  /** Einzelnes Laden mit `loading`/`error` wie in Nuxt; Fehler werden an den Aufrufer weitergereicht. */
  private async load<T>(request: () => Promise<T>, fallback: string): Promise<T> {
    this.loading.set(true);
    this.error.set(null);
    try {
      return await request();
    } catch (err) {
      this.error.set(getErrorMessage(err, fallback));
      throw err;
    } finally {
      this.loading.set(false);
    }
  }

  /** Mutation ohne `loading` (die Liste bleibt sichtbar); setzt bei Fehler die Meldung und wirft. */
  private async mutate(request: () => Promise<unknown>, fallback: string): Promise<void> {
    try {
      await request();
    } catch (err) {
      this.error.set(getErrorMessage(err, fallback));
      throw err;
    }
  }
}
