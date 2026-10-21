import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { getErrorMessage } from '../../core/errors';
import { Group, User } from '../../core/models';

const API = '/api/v2/groups';

export type GroupSearchResult = Group & { member_count?: number };

/** Gruppen und Mitglieder (Port von `stores/groups.ts` und `useGroups`); Zustand als Signals. */
@Injectable({ providedIn: 'root' })
export class GroupStore {
  private readonly http = inject(HttpClient);

  readonly groups = signal<Group[]>([]);
  readonly currentGroup = signal<Group | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly groupsCount = computed(() => this.groups().length);
  readonly currentGroupMemberCount = computed(() => this.currentGroup()?.members?.length ?? 0);

  async fetchGroups(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.groups.set((await firstValueFrom(this.http.get<Group[] | null>(API))) ?? []);
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Laden der Gruppen'));
      throw err;
    } finally {
      this.loading.set(false);
    }
  }

  /** Lädt die Mitglieder einer Gruppe und macht sie zur aktuellen; kennt der Store die Gruppe nicht, lädt er zuerst die Liste. */
  async fetchGroup(groupId: number): Promise<Group | null> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const members = (await firstValueFrom(this.http.get<User[] | null>(`${API}/${groupId}/users`))) ?? [];
      if (!this.findGroup(groupId)) await this.fetchGroups();
      const group = this.findGroup(groupId);
      if (group) {
        const current = { ...group, members };
        this.currentGroup.set(current);
        this.groups.update((list) => list.map((g) => (g.id === groupId ? current : g)));
      }
      return this.currentGroup();
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Laden der Gruppe'));
      throw err;
    } finally {
      this.loading.set(false);
    }
  }

  async createGroup(name: string): Promise<Group> {
    const group = await this.mutate(() => firstValueFrom(this.http.post<Group>(API, { name })), 'Fehler beim Erstellen der Gruppe');
    if (group) this.groups.update((list) => [...list, group]);
    return group;
  }

  async deleteGroup(groupId: number): Promise<void> {
    await this.mutate(() => firstValueFrom(this.http.delete(`${API}/${groupId}`)), 'Fehler beim Löschen der Gruppe');
    this.groups.update((list) => list.filter((g) => g.id !== groupId));
  }

  async addMember(groupId: number, userId: number): Promise<void> {
    await this.mutate(() => firstValueFrom(this.http.post(`${API}/${groupId}/user/${userId}`, null)), 'Fehler beim Hinzufügen des Mitglieds');
    await this.refresh(groupId);
  }

  async removeMember(groupId: number, userId: number): Promise<void> {
    await this.mutate(() => firstValueFrom(this.http.delete(`${API}/${groupId}/user/${userId}`)), 'Fehler beim Entfernen des Mitglieds');
    await this.refresh(groupId);
  }

  /** Wie `removeMember` für die eigene Person; die Navigation übernimmt der Aufrufer. */
  async leaveGroup(groupId: number, userId: number): Promise<void> {
    await this.mutate(() => firstValueFrom(this.http.delete(`${API}/${groupId}/user/${userId}`)), 'Fehler beim Verlassen der Gruppe');
    this.groups.update((list) => list.filter((g) => g.id !== groupId));
    this.currentGroup.set(null);
  }

  /** Sucht Gruppen für die Teilnehmer-Auswahl; verändert den Store-Zustand nicht (Port von `useGroups.searchGroups`). */
  async searchGroups(search: string): Promise<GroupSearchResult[]> {
    return (await firstValueFrom(this.http.get<GroupSearchResult[] | null>(`${API}/`, { params: { search } }))) ?? [];
  }

  clearCurrentGroup(): void {
    this.currentGroup.set(null);
  }

  clearError(): void {
    this.error.set(null);
  }

  private findGroup(groupId: number): Group | undefined {
    return this.groups().find((g) => g.id === groupId);
  }

  /** Lädt die geöffnete Gruppe neu, sonst die Liste. */
  private refresh(groupId: number): Promise<unknown> {
    return this.currentGroup()?.id === groupId ? this.fetchGroup(groupId) : this.fetchGroups();
  }

  /** Mutation ohne `loading` (die Liste bleibt sichtbar); setzt bei Fehler die Meldung und wirft. */
  private async mutate<T>(request: () => Promise<T>, fallback: string): Promise<T> {
    try {
      return await request();
    } catch (err) {
      this.error.set(getErrorMessage(err, fallback));
      throw err;
    }
  }
}
