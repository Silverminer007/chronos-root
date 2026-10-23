import { ChangeDetectionStrategy, Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { getErrorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { FriendshipStore } from './friendship.store';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Dialog zum Senden einer Freundschaftsanfrage per E-Mail (Port von `SendFriendRequestDialog.vue`). */
@Component({
  selector: 'app-send-friend-request-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="send-request-title">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <span class="icon-tile me-3" aria-hidden="true"><i class="bi bi-person-plus fs-4"></i></span>
              <h2 class="modal-title fs-5 flex-grow-1" id="send-request-title">Freund hinzufügen</h2>
              <button type="button" class="btn-close" aria-label="Schließen" (click)="close()"></button>
            </div>
            <div class="modal-body">
              <label for="friend-email" class="form-label fw-medium">E-Mail-Adresse</label>
              <div class="input-group">
                <span class="input-group-text"><i class="bi bi-envelope" aria-hidden="true"></i></span>
                <input
                  id="friend-email"
                  type="email"
                  class="form-control"
                  placeholder="freund@beispiel.de"
                  [value]="email()"
                  (input)="email.set($any($event.target).value)"
                  (keyup.enter)="send()"
                />
              </div>
              <p class="small text-body-secondary mt-2 mb-0">
                Gib die E-Mail-Adresse der Person ein, der du eine Freundschaftsanfrage senden möchtest.
              </p>
              @if (error()) {
                <div class="alert alert-danger small mt-3 mb-0" role="alert">{{ error() }}</div>
              }
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="close()">Abbrechen</button>
              <button type="button" class="btn btn-gradient" [disabled]="!valid() || loading()" (click)="send()">
                @if (loading()) {
                  <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
                } @else {
                  <i class="bi bi-send me-2" aria-hidden="true"></i>
                }
                Anfrage senden
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class SendFriendRequestDialogComponent {
  private readonly store = inject(FriendshipStore);
  private readonly toast = inject(ToastService);

  readonly visible = input.required<boolean>();
  readonly closed = output<void>();

  protected readonly email = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly valid = computed(() => EMAIL_PATTERN.test(this.email()));

  constructor() {
    effect(() => {
      if (!this.visible()) untracked(() => this.reset());
    });
  }

  protected async send(): Promise<void> {
    if (!this.valid()) {
      this.error.set('Bitte gib eine gültige E-Mail-Adresse ein');
      return;
    }
    const email = this.email();
    this.loading.set(true);
    this.error.set('');
    try {
      await this.store.sendRequest(email);
      this.toast.success('Anfrage gesendet', `Freundschaftsanfrage an ${email} wurde gesendet`);
      this.close();
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Senden der Anfrage. Überprüfe die E-Mail-Adresse.'));
    } finally {
      this.loading.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected close(): void {
    if (!this.visible()) return;
    this.reset();
    this.closed.emit();
  }

  private reset(): void {
    this.email.set('');
    this.error.set('');
  }
}
