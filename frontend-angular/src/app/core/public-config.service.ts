import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface ImpressumConfig {
  name: string;
  street: string;
  city: string;
  email: string;
  phone: string;
}

const EMPTY_IMPRESSUM: ImpressumConfig = { name: '', street: '', city: '', email: '', phone: '' };

/** Öffentliche Laufzeit-Konfiguration des BFF (`GET /api/config`), z. B. die Impressum-Angaben. */
@Injectable({ providedIn: 'root' })
export class PublicConfigService {
  private readonly http = inject(HttpClient);
  private loading: Promise<void> | null = null;

  readonly impressum = signal<ImpressumConfig>(EMPTY_IMPRESSUM);

  /** Lädt die Konfiguration einmalig; bei einem Fehler bleiben die Werte leer. */
  loadImpressum(): Promise<void> {
    this.loading ??= firstValueFrom(this.http.get<{ impressum: ImpressumConfig }>('/api/config')).then(
      (config) => this.impressum.set({ ...EMPTY_IMPRESSUM, ...config.impressum }),
      () => undefined,
    );
    return this.loading;
  }
}
