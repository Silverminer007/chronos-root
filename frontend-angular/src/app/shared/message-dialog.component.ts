import { ChangeDetectionStrategy, Component, HostListener, computed, input, output, signal } from '@angular/core';

const MAX_LENGTH = 500;

/** Bootstrap-Modal zum Verfassen einer Nachricht an alle Teilnehmenden (Port von `MessageDialog.vue`). */
@Component({
  selector: 'app-message-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="message-dialog-title">
        <div class="modal-dialog modal-dialog-centered modal-lg">
          <div class="modal-content">
            <div class="modal-header">
              <span class="icon-tile me-3" aria-hidden="true"><i class="bi bi-send fs-4"></i></span>
              <div class="flex-grow-1">
                <h2 class="modal-title fs-5" id="message-dialog-title">Nachricht senden</h2>
                <p class="small text-body-secondary mb-0">{{ appointmentTitle() }}</p>
              </div>
              <button type="button" class="btn-close" aria-label="Schließen" (click)="closed.emit()"></button>
            </div>
            <div class="modal-body">
              <div class="alert alert-primary d-flex gap-3" role="note">
                <i class="bi bi-info-circle" aria-hidden="true"></i>
                <div>
                  <p class="fw-medium mb-1">Empfänger</p>
                  <p class="mb-0 small">Alle Teilnehmer dieses Termins ({{ recipientCount() }} Personen)</p>
                </div>
              </div>
              <label for="message-dialog-text" class="form-label fw-medium">Nachricht</label>
              <textarea
                id="message-dialog-text"
                class="form-control"
                rows="6"
                placeholder="Schreibe hier deine Nachricht an alle Teilnehmer..."
                [value]="message()"
                (input)="message.set($any($event.target).value)"
              ></textarea>
              <div class="d-flex justify-content-between mt-2 small">
                <span class="text-body-secondary">{{ message().length }} / {{ maxLength }} Zeichen</span>
                @if (message().length > maxLength) {
                  <span class="text-danger">Maximale Länge überschritten</span>
                }
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="closed.emit()">Abbrechen</button>
              <button type="button" class="btn btn-gradient" [disabled]="!isValid()" (click)="send()">
                <i class="bi bi-send me-2" aria-hidden="true"></i>Nachricht senden
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class MessageDialogComponent {
  readonly visible = input.required<boolean>();
  readonly appointmentTitle = input.required<string>();
  readonly recipientCount = input.required<number>();

  readonly closed = output<void>();
  readonly sent = output<{ message: string }>();

  protected readonly maxLength = MAX_LENGTH;
  protected readonly message = signal('');
  protected readonly isValid = computed(() => this.message().trim().length > 0 && this.message().length <= MAX_LENGTH);

  protected send(): void {
    if (!this.isValid()) return;
    this.sent.emit({ message: this.message() });
    this.message.set('');
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.visible()) this.closed.emit();
  }
}
