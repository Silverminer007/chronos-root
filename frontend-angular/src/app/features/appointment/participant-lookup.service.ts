import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Group } from '../../core/models';

export type GroupSearchResult = Group & { member_count?: number };

/** Sucht Gruppen, die einem Termin hinzugefügt werden können (Port von `useGroups`); Freunde liefert der `FriendshipStore`. */
@Injectable({ providedIn: 'root' })
export class ParticipantLookupService {
  private readonly http = inject(HttpClient);

  async searchGroups(search: string): Promise<GroupSearchResult[]> {
    return (await firstValueFrom(this.http.get<GroupSearchResult[] | null>('/api/v2/groups/', { params: { search } }))) ?? [];
  }
}
