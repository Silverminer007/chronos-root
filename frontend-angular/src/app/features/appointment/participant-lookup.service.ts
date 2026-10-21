import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Friend, Group } from '../../core/models';

export type GroupSearchResult = Group & { member_count?: number };

/** Sucht Freunde und Gruppen, die einem Termin hinzugefügt werden können (Port von `useFriends`/`useGroups`). */
@Injectable({ providedIn: 'root' })
export class ParticipantLookupService {
  private readonly http = inject(HttpClient);

  loadFriends(): Promise<Friend[]> {
    return firstValueFrom(this.http.get<Friend[]>('/api/v2/friendships/friends/'));
  }

  async searchGroups(search: string): Promise<GroupSearchResult[]> {
    return (await firstValueFrom(this.http.get<GroupSearchResult[] | null>('/api/v2/groups/', { params: { search } }))) ?? [];
  }
}
