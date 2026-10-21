import { Injectable, signal } from '@angular/core';

export type ToastSeverity = 'success' | 'info' | 'warn' | 'error';

export interface ToastMessage {
  severity: ToastSeverity;
  summary: string;
  detail?: string;
  /** Anzeigedauer in Millisekunden. */
  life?: number;
}

export interface Toast extends ToastMessage {
  id: number;
}

/** Zentrale Toast-Meldungen (Ersatz für PrimeVue `useToast`); angezeigt von `ToastContainerComponent`. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  static readonly DEFAULT_LIFE_MS = 3000;

  private readonly items = signal<Toast[]>([]);
  private nextId = 1;

  readonly toasts = this.items.asReadonly();

  add(message: ToastMessage): void {
    const toast: Toast = { ...message, id: this.nextId++ };
    this.items.update((list) => [...list, toast]);
    setTimeout(() => this.remove(toast.id), message.life ?? ToastService.DEFAULT_LIFE_MS);
  }

  remove(id: number): void {
    this.items.update((list) => list.filter((t) => t.id !== id));
  }

  success(summary: string, detail?: string): void {
    this.add({ severity: 'success', summary, detail });
  }

  info(summary: string, detail?: string): void {
    this.add({ severity: 'info', summary, detail });
  }

  warn(summary: string, detail?: string): void {
    this.add({ severity: 'warn', summary, detail });
  }

  error(summary: string, detail?: string): void {
    this.add({ severity: 'error', summary, detail });
  }
}
