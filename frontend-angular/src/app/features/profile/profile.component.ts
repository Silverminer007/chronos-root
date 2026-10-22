import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { LinkedAccount, Passkey } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';

interface Provider {
  id: string;
  name: string;
  icon: string;
}

/** Seite `/profile`: Stammdaten, verknüpfte Konten und Passkeys (Port von `pages/profile.vue`). */
@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  protected readonly firstName = signal(this.auth.user()?.first_name ?? '');
  protected readonly lastName = signal(this.auth.user()?.last_name ?? '');
  protected readonly email = signal(this.auth.user()?.email ?? '');
  protected readonly savingProfile = signal(false);
  protected readonly initials = computed(() => `${this.firstName().charAt(0)}${this.lastName().charAt(0)}`);

  // Microsoft und Apple sind wie in Nuxt (noch) nicht freigeschaltet.
  protected readonly providers: Provider[] = [{ id: 'google', name: 'Google', icon: 'bi-google' }];
  protected readonly linkedAccounts = signal<LinkedAccount[]>([]);
  protected readonly loadingLinked = signal(false);
  protected readonly unlinkingProvider = signal<string | null>(null);

  protected readonly passkeys = signal<Passkey[]>([]);
  protected readonly loadingPasskeys = signal(false);

  ngOnInit(): void {
    void this.loadLinkedAccounts();
    void this.loadPasskeys();
  }

  protected isLinked(providerId: string): boolean {
    return this.linkedAccounts().some((account) => account.provider === providerId);
  }

  protected formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  protected async saveProfile(): Promise<void> {
    this.savingProfile.set(true);
    try {
      const verifyEmailUrl = await this.auth.updateProfile({
        first_name: this.firstName(),
        last_name: this.lastName(),
        email: this.email(),
      });
      this.toast.add({ severity: 'success', summary: 'Profil gespeichert', life: 3000 });
      if (verifyEmailUrl) this.auth.redirectTo(verifyEmailUrl);
    } catch {
      this.toast.add({ severity: 'error', summary: 'Fehler beim Speichern', detail: 'Bitte versuche es erneut', life: 3000 });
    } finally {
      this.savingProfile.set(false);
    }
  }

  protected async unlink(providerId: string): Promise<void> {
    this.unlinkingProvider.set(providerId);
    try {
      await this.auth.unlinkAccount(providerId);
      this.linkedAccounts.update((accounts) => accounts.filter((account) => account.provider !== providerId));
      this.toast.add({ severity: 'success', summary: 'Konto getrennt', life: 3000 });
    } catch {
      this.toast.add({ severity: 'error', summary: 'Fehler', detail: 'Konto konnte nicht getrennt werden', life: 3000 });
    } finally {
      this.unlinkingProvider.set(null);
    }
  }

  protected changePassword(): Promise<void> {
    return this.leaveFor(() => this.auth.changePassword());
  }

  protected link(providerId: string): Promise<void> {
    return this.leaveFor(() => this.auth.linkAccount(providerId));
  }

  protected createPasskey(): Promise<void> {
    return this.leaveFor(() => this.auth.createPasskey());
  }

  protected deletePasskey(passkeyId: string): Promise<void> {
    return this.leaveFor(() => this.auth.deletePasskey(passkeyId));
  }

  /** Holt die Keycloak-URL der Aktion und wechselt dorthin; Keycloak führt danach hierher zurück. */
  private async leaveFor(fetchUrl: () => Promise<string>): Promise<void> {
    try {
      this.auth.redirectTo(await fetchUrl());
    } catch {
      this.toast.add({ severity: 'error', summary: 'Fehler', detail: 'Aktion konnte nicht gestartet werden', life: 3000 });
    }
  }

  private async loadLinkedAccounts(): Promise<void> {
    this.loadingLinked.set(true);
    try {
      this.linkedAccounts.set(await this.auth.fetchLinkedAccounts());
    } catch {
      // Der Abschnitt bleibt leer.
    } finally {
      this.loadingLinked.set(false);
    }
  }

  private async loadPasskeys(): Promise<void> {
    this.loadingPasskeys.set(true);
    try {
      this.passkeys.set(await this.auth.fetchPasskeys());
    } catch {
      // Der Abschnitt bleibt leer.
    } finally {
      this.loadingPasskeys.set(false);
    }
  }
}
