import { Injectable } from '@angular/core';

export interface NewFeature {
  id: string;
  /** Bootstrap-Icons-Klasse, z. B. `bi-person`. */
  icon: string;
  title: string;
  description: string;
  link?: string;
  linkLabel?: string;
}

const STORAGE_KEY = 'shownFeatureIds';

// Neue Einträge hier ergänzen – bestehende Nutzer sehen sie beim nächsten Login.
const ALL_FEATURES: NewFeature[] = [
  {
    id: 'profile-page-v1',
    icon: 'bi-person',
    title: 'Dein Profil',
    description: 'Ab sofort kannst du deinen Namen und deine E-Mail-Adresse direkt in der App bearbeiten.',
    link: '/profile',
    linkLabel: 'Zum Profil',
  },
  {
    id: 'linked-accounts-v1',
    icon: 'bi-link-45deg',
    title: 'Verknüpfte Konten',
    description: 'Verbinde dein Konto mit Google, Microsoft oder Apple – für eine schnellere Anmeldung ohne Passwort.',
    link: '/profile',
    linkLabel: 'Konten verknüpfen',
  },
  {
    id: 'passkeys-v1',
    icon: 'bi-fingerprint',
    title: 'Passkeys',
    description: 'Melde dich per Fingerabdruck, Gesichtserkennung oder Geräte-PIN an – ganz ohne Passwort.',
    link: '/profile',
    linkLabel: 'Passkey einrichten',
  },
  {
    id: 'change-password-v1',
    icon: 'bi-lock',
    title: 'Passwort ändern',
    description: 'Dein Passwort kannst du jetzt direkt in Chronos ändern – ohne Umwege über externe Seiten.',
    link: '/profile',
    linkLabel: 'Passwort ändern',
  },
];

/** Merkt, welche Neuigkeiten der Nutzer schon gesehen hat (Port von `useNewFeatures`). */
@Injectable({ providedIn: 'root' })
export class NewFeaturesService {
  getUnshownFeatures(): NewFeature[] {
    const shown = this.shownIds();
    return ALL_FEATURES.filter((feature) => !shown.includes(feature.id));
  }

  markShown(id: string): void {
    const shown = this.shownIds();
    if (!shown.includes(id)) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify([...shown, id]));
    }
  }

  markAllShown(): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(ALL_FEATURES.map((feature) => feature.id)));
  }

  private shownIds(): string[] {
    try {
      const ids: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      return Array.isArray(ids) ? ids : [];
    } catch {
      return [];
    }
  }
}
