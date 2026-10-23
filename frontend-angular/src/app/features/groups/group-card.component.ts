import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ModalService } from '../../core/modal.service';
import { Group } from '../../core/models';

const MAX_AVATARS = 5;

/** Karte einer Gruppe in der Übersicht mit „Löschen“ (Port von `GroupCard.vue`). */
@Component({
  selector: 'app-group-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <a class="card shadow-sm text-reset text-decoration-none group-card" [routerLink]="['/groups', group().id]">
      <div class="card-body d-flex align-items-start gap-3">
        <span class="icon-tile flex-shrink-0" aria-hidden="true"><i class="bi bi-people fs-4"></i></span>
        <div class="flex-grow-1 min-w-0">
          <div class="d-flex align-items-center justify-content-between gap-2">
            <h3 class="h6 fw-semibold mb-0 text-truncate">{{ group().name }}</h3>
            <button
              type="button"
              class="btn btn-outline-danger border-0 flex-shrink-0"
              title="Gruppe löschen"
              aria-label="Gruppe löschen"
              (click)="confirmDelete($event)"
            >
              <i class="bi bi-trash" aria-hidden="true"></i>
            </button>
          </div>
          <p class="small text-body-secondary mt-1 mb-0">{{ memberCount() }} {{ memberCount() === 1 ? 'Mitglied' : 'Mitglieder' }}</p>
          @if (memberCount() > 0) {
            <div class="d-flex avatar-stack mt-3">
              @for (member of displayedMembers(); track member.id) {
                <span class="avatar" data-testid="member-avatar" [title]="member.first_name + ' ' + member.last_name"
                  >{{ member.first_name?.charAt(0) }}{{ member.last_name?.charAt(0) }}</span
                >
              }
              @if (remainingCount() > 0) {
                <span class="avatar avatar-more">+{{ remainingCount() }}</span>
              }
            </div>
          }
        </div>
        <i class="bi bi-chevron-right text-body-tertiary flex-shrink-0 align-self-center" aria-hidden="true"></i>
      </div>
    </a>
  `,
})
export class GroupCardComponent {
  private readonly modal = inject(ModalService);

  readonly group = input.required<Group>();
  readonly deleted = output<number>();

  protected readonly memberCount = computed(() => this.group().members?.length ?? 0);
  protected readonly displayedMembers = computed(() => (this.group().members ?? []).slice(0, MAX_AVATARS));
  protected readonly remainingCount = computed(() => Math.max(0, this.memberCount() - MAX_AVATARS));

  protected async confirmDelete(event: Event): Promise<void> {
    // Der Button liegt in einem Link; ohne das würde die Detailseite geöffnet.
    event.preventDefault();
    event.stopPropagation();
    const confirmed = await this.modal.confirm({
      title: 'Gruppe löschen',
      message: `Möchtest du die Gruppe '${this.group().name}' wirklich löschen? Alle Mitglieder werden entfernt.`,
      confirmText: 'Löschen',
      confirmColor: 'red',
    });
    if (confirmed) this.deleted.emit(this.group().id);
  }
}
