import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DialogQueueService } from './dialog-queue.service';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/** Bietet die Installation der PWA an, sobald der Browser `beforeinstallprompt` sendet (Port von `PwaInstallDialog.vue`). */
@Component({
  selector: 'app-pwa-install-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(window:beforeinstallprompt)': 'onBeforeInstallPrompt($event)' },
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="pwa-install-title">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-body p-4">
              <div class="d-flex align-items-center gap-3 mb-3">
                <span class="icon-tile flex-shrink-0" aria-hidden="true"><i class="bi bi-download fs-4"></i></span>
                <h2 class="h5 fw-bold mb-0" id="pwa-install-title">App installieren</h2>
              </div>
              <p class="small text-body-secondary mb-4">
                Installiere Chronos für einen schnelleren Zugriff und ein besseres Benutzererlebnis. Du kannst die App direkt vom Homescreen öffnen.
              </p>
              <div class="d-flex flex-column flex-sm-row justify-content-end gap-2">
                <button type="button" class="btn btn-outline-primary" (click)="close()">Später</button>
                <button type="button" class="btn btn-gradient" (click)="install()">Jetzt installieren</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class PwaInstallDialogComponent {
  private readonly queue = inject(DialogQueueService);
  private readonly wanted = signal(false);
  protected readonly visible = computed(() => this.wanted() && this.queue.active() === 'pwaInstall');
  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  protected onBeforeInstallPrompt(event: Event): void {
    event.preventDefault();
    this.deferredPrompt = event as BeforeInstallPromptEvent;
    this.wanted.set(true);
    this.queue.claim('pwaInstall');
  }

  protected async install(): Promise<void> {
    const deferred = this.deferredPrompt;
    if (!deferred) return;
    void deferred.prompt();
    await deferred.userChoice;
    this.deferredPrompt = null;
    this.hide();
  }

  protected close(): void {
    this.hide();
  }

  private hide(): void {
    this.wanted.set(false);
    this.queue.release('pwaInstall');
  }
}
