import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { User } from '../../core/models';

/** Karte eines Gruppenmitglieds mit optionalem „Entfernen“ (Port von `GroupMemberCard.vue`). */
@Component({
  selector: 'app-group-member-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card">
      <div class="card-body d-flex align-items-center gap-3 p-3">
        <span class="avatar flex-shrink-0" aria-hidden="true">{{ initials() }}</span>
        <p class="fw-medium mb-0 flex-grow-1 min-w-0 text-truncate">{{ fullName() }}</p>
        @if (canRemove()) {
          <button
            type="button"
            class="btn btn-outline-danger border-0"
            title="Mitglied entfernen"
            aria-label="Mitglied entfernen"
            (click)="remove.emit(member().id)"
          >
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        }
      </div>
    </div>
  `,
})
export class GroupMemberCardComponent {
  readonly member = input.required<User>();
  readonly canRemove = input.required<boolean>();
  readonly remove = output<number>();

  protected readonly fullName = computed(() => `${this.member().first_name ?? ''} ${this.member().last_name ?? ''}`.trim());
  protected readonly initials = computed(() => `${this.member().first_name?.charAt(0) ?? ''}${this.member().last_name?.charAt(0) ?? ''}`);
}
