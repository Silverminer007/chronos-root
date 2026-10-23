import { Injectable, computed, signal } from '@angular/core';

/** Reihenfolge der Erststart-Dialoge: kleinere Zahl zuerst. */
export const DIALOG_PRIORITY = { push: 0, features: 1, pwaInstall: 2 } as const;
export type DialogId = keyof typeof DIALOG_PRIORITY;

/**
 * Sorgt dafür, dass immer nur ein Layout-Dialog sichtbar ist (#208). Dialoge melden sich synchron mit `claim` an
 * (auch wenn sie erst noch prüfen, ob sie überhaupt erscheinen) und geben mit `release` frei; sichtbar darf nur der
 * Dialog mit der höchsten Priorität sein, die übrigen erscheinen nacheinander.
 */
@Injectable({ providedIn: 'root' })
export class DialogQueueService {
  private readonly claims = signal<readonly DialogId[]>([]);

  /** Der Dialog, der gerade an der Reihe ist. */
  readonly active = computed<DialogId | undefined>(() =>
    [...this.claims()].sort((a, b) => DIALOG_PRIORITY[a] - DIALOG_PRIORITY[b])[0],
  );

  claim(id: DialogId): void {
    if (!this.claims().includes(id)) this.claims.update((ids) => [...ids, id]);
  }

  release(id: DialogId): void {
    this.claims.update((ids) => ids.filter((claimed) => claimed !== id));
  }
}
