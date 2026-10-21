import { Injectable, signal } from '@angular/core';

export type ConfirmColor = 'red' | 'purple' | 'green';

export interface ConfirmOptions {
  title: string;
  message: string;
  confirmText: string;
  confirmColor?: ConfirmColor;
}

export interface ConfirmRequest extends ConfirmOptions {
  confirmColor: ConfirmColor;
}

/** Öffnet Bestätigungsdialoge (Ersatz für `ConfirmDialog.vue`); gerendert von `ConfirmDialogComponent`. */
@Injectable({ providedIn: 'root' })
export class ModalService {
  private readonly request = signal<ConfirmRequest | null>(null);
  private pending: ((confirmed: boolean) => void) | null = null;

  readonly confirmRequest = this.request.asReadonly();

  /** Löst mit `true` bei Bestätigung, mit `false` bei Abbruch auf. */
  confirm(options: ConfirmOptions): Promise<boolean> {
    this.pending?.(false);
    return new Promise<boolean>((resolve) => {
      this.pending = resolve;
      this.request.set({ confirmColor: 'purple', ...options });
    });
  }

  resolve(confirmed: boolean): void {
    const pending = this.pending;
    this.pending = null;
    this.request.set(null);
    pending?.(confirmed);
  }
}
