import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const THEME_ATTRIBUTE = 'data-bs-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

/** Steuert den Bootstrap-Dark-Mode über `data-bs-theme` am `<html>`-Element (Q-01). */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly current = signal<Theme>('light');

  readonly theme = this.current.asReadonly();

  /** Übernimmt das Farbschema des Systems und folgt späteren Änderungen. */
  init(): void {
    const query = this.document.defaultView?.matchMedia?.(DARK_QUERY);
    this.set(query?.matches ? 'dark' : 'light');
    query?.addEventListener?.('change', (event) => this.set(event.matches ? 'dark' : 'light'));
  }

  set(theme: Theme): void {
    this.current.set(theme);
    this.document.documentElement.setAttribute(THEME_ATTRIBUTE, theme);
  }
}
