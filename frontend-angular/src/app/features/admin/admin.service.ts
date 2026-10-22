import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminStatistics, AdminUserResponse, PushLogEntry } from './admin.models';

const API = '/api/v2/admin';

/** Kennung der laufenden Umfrage, für die Einladungen versendet werden (wie in Nuxt fest hinterlegt). */
export const SURVEY_ID = '11111111-1111-1111-1111-111111111111';

/** Zugriff auf die Admin-Endpunkte; die Rollenprüfung (`ADMIN_API`) liegt allein im Backend. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  statistics(): Promise<AdminStatistics> {
    return firstValueFrom(this.http.get<AdminStatistics>(`${API}/statistics`));
  }

  users(size: number): Promise<AdminUserResponse> {
    return firstValueFrom(this.http.get<AdminUserResponse>(`${API}/user/`, { params: { size } }));
  }

  pushLog(): Promise<PushLogEntry[]> {
    return firstValueFrom(this.http.get<PushLogEntry[]>(`${API}/push/log`));
  }

  sendSurveyInvitations(): Promise<unknown> {
    return firstValueFrom(this.http.post(`${API}/survey/${SURVEY_ID}/notify`, null));
  }
}
