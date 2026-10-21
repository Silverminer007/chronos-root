import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { formatDateTime } from '../../core/date-format';
import { FriendshipRequest } from '../../core/models';

/** Karte einer ein- oder ausgehenden Freundschaftsanfrage (Port von `FriendRequestCard.vue`). */
@Component({
  selector: 'app-friend-request-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card shadow-sm">
      <div class="card-body d-flex align-items-center gap-3">
        <span
          class="avatar flex-shrink-0"
          [class.text-success]="type() === 'incoming'"
          [class.text-primary]="type() === 'outgoing'"
          aria-hidden="true"
          >{{ initial() }}</span
        >
        <div class="flex-grow-1 min-w-0">
          <p class="fw-semibold mb-0 text-truncate">{{ request().userName }}</p>
          <p class="small text-body-secondary mb-0">
            {{ type() === 'incoming' ? 'Möchte dein Freund werden' : 'Anfrage gesendet' }}
          </p>
          @if (date(); as created) {
            <p class="small text-body-tertiary mb-0 mt-1">{{ created }}</p>
          }
        </div>
        @if (type() === 'incoming') {
          <div class="d-flex align-items-center gap-2 flex-shrink-0">
            <button type="button" class="btn btn-success btn-sm" [disabled]="loading()" (click)="act(accepted)">
              <i class="bi bi-check-lg me-1" aria-hidden="true"></i>Annehmen
            </button>
            <button type="button" class="btn btn-outline-danger btn-sm" [disabled]="loading()" (click)="act(declined)">
              <i class="bi bi-x-lg me-1" aria-hidden="true"></i>Ablehnen
            </button>
          </div>
        } @else {
          <button type="button" class="btn btn-outline-secondary btn-sm flex-shrink-0" [disabled]="loading()" (click)="act(cancelled)">
            Zurückziehen
          </button>
        }
      </div>
    </div>
  `,
})
export class FriendRequestCardComponent {
  readonly request = input.required<FriendshipRequest>();
  readonly type = input.required<'incoming' | 'outgoing'>();
  readonly accepted = output<number>();
  readonly declined = output<number>();
  readonly cancelled = output<number>();

  /** Wie in Nuxt bleiben die Schaltflächen nach dem Klick gesperrt; die Seite entfernt die Karte bei Erfolg. */
  protected readonly loading = signal(false);
  protected readonly initial = computed(() => this.request().userName?.charAt(0)?.toUpperCase() || '?');
  protected readonly date = computed(() => {
    const created = this.request().createdAt;
    return created ? formatDateTime(created) : '';
  });

  protected act(output: { emit(value: number): void }): void {
    this.loading.set(true);
    output.emit(this.request().requestId);
  }
}
