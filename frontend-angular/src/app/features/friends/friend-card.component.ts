import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { formatDate } from '../../core/date-format';
import { Friend } from '../../core/models';
import { ModalService } from '../../core/modal.service';

/** Karte eines Freundes mit „Freundschaft beenden“ (Port von `FriendCard.vue`). */
@Component({
  selector: 'app-friend-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card shadow-sm">
      <div class="card-body d-flex align-items-center gap-3">
        <span class="avatar flex-shrink-0" aria-hidden="true">{{ initial() }}</span>
        <div class="flex-grow-1 min-w-0">
          <p class="fw-semibold mb-0 text-truncate">{{ friend().name }}</p>
          <p class="small text-body-secondary mb-0 text-truncate">{{ friend().email }}</p>
          @if (friendsSince(); as since) {
            <p class="small text-body-tertiary mb-0 mt-1">Freunde seit {{ since }}</p>
          }
        </div>
        <button
          type="button"
          class="btn btn-outline-danger border-0"
          title="Freundschaft beenden"
          aria-label="Freundschaft beenden"
          (click)="confirmRemove()"
        >
          <i class="bi bi-trash" aria-hidden="true"></i>
        </button>
      </div>
    </div>
  `,
})
export class FriendCardComponent {
  private readonly modal = inject(ModalService);

  readonly friend = input.required<Friend>();
  readonly remove = output<number>();

  protected readonly initial = computed(() => this.friend().name?.charAt(0)?.toUpperCase() || '?');
  protected readonly friendsSince = computed(() => {
    const since = this.friend().friends_since;
    return since ? formatDate(since) : '';
  });

  protected async confirmRemove(): Promise<void> {
    const confirmed = await this.modal.confirm({
      title: 'Freundschaft beenden',
      message: `Möchtest du die Freundschaft mit ${this.friend().name} wirklich beenden?`,
      confirmText: 'Beenden',
      confirmColor: 'red',
    });
    if (confirmed) this.remove.emit(this.friend().user_id);
  }
}
