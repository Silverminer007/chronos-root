import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LinkedAccount, Passkey, User } from './models';

export interface ProfilePayload {
  first_name: string;
  last_name: string;
  email: string;
}

const SESSION_EXPIRED = 'SESSION_EXPIRED';

/** Sitzung und Benutzerprofil (Port von `stores/auth.ts`); Zustand als Signals. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly user = signal<User | null>(null);

  /** Fragt `/api/auth/isLoggedIn` ab; der Service Worker beantwortet das ohne Server-Roundtrip. */
  async checkSession(): Promise<boolean> {
    try {
      await firstValueFrom(this.http.get('/api/auth/isLoggedIn', { responseType: 'text' }));
      return true;
    } catch {
      return false;
    }
  }

  async fetchUser(): Promise<void> {
    try {
      this.user.set((await firstValueFrom(this.http.get<User | null>('/api/v2/user'))) ?? null);
    } catch {
      this.user.set(null);
    }
  }

  /** Das Profil ist vollständig, wenn Vor- und Nachname gesetzt sind. */
  isProfileComplete(): boolean {
    const user = this.user();
    return !!user?.first_name?.trim() && !!user.last_name?.trim();
  }

  /** Speichert das Profil; liefert die Keycloak-URL zur E-Mail-Verifizierung, falls nötig. */
  async updateProfile(payload: ProfilePayload): Promise<string | undefined> {
    const { user, verifyEmailUrl } = await firstValueFrom(
      this.http.patch<{ user?: User; verifyEmailUrl?: string }>('/api/v2/user', payload, {
        params: this.redirectParams(),
      }),
    );
    if (user) this.user.set(user);
    return verifyEmailUrl;
  }

  async changePassword(): Promise<string> {
    return this.fetchUrl('/api/v2/user/change-password');
  }

  fetchLinkedAccounts(): Promise<LinkedAccount[]> {
    return firstValueFrom(this.http.get<LinkedAccount[]>('/api/v2/user/linked'));
  }

  async unlinkAccount(providerId: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/v2/user/linked/${providerId}`));
  }

  linkAccount(providerId: string): Promise<string> {
    return this.fetchUrl(`/api/v2/user/link/${providerId}`);
  }

  fetchPasskeys(): Promise<Passkey[]> {
    return firstValueFrom(this.http.get<Passkey[]>('/api/v2/user/passkeys'));
  }

  createPasskey(): Promise<string> {
    return this.fetchUrl('/api/v2/user/link/passkey');
  }

  deletePasskey(passkeyId: string): Promise<string> {
    return this.fetchUrl('/api/v2/user/unlink/passkey', { passkeyId });
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', null, { responseType: 'text' }));
      this.user.set(null);
    } catch (err) {
      console.error('Logout fehlgeschlagen:', err);
    }
    this.redirectTo('/');
  }

  /** Navigiert bei `SESSION_EXPIRED` des Service Workers auf `/` (Port von `plugins/auth.client.ts`). */
  listenForSessionExpiry(container: ServiceWorkerContainer | undefined = navigator.serviceWorker): void {
    container?.addEventListener('message', (event) => {
      if ((event as MessageEvent<{ type?: string }>).data?.type === SESSION_EXPIRED) {
        void this.router.navigateByUrl('/');
      }
    });
  }

  /** Vollständiger Seitenwechsel (z. B. zu Keycloak); eigene Methode, damit Tests ihn ersetzen können. */
  redirectTo(url: string): void {
    window.location.href = url;
  }

  private async fetchUrl(path: string, extra: Record<string, string> = {}): Promise<string> {
    const { url } = await firstValueFrom(
      this.http.get<{ url: string }>(path, { params: { ...this.redirectParams(), ...extra } }),
    );
    return url;
  }

  private redirectParams(): Record<string, string> {
    return { redirectUri: window.location.origin + window.location.pathname };
  }
}
