import { ChangeDetectionStrategy, Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { Friend } from '../../core/models';
import { FriendshipStore } from '../friends/friendship.store';

const MIN_QUERY_LENGTH = 2;

/** Dialog zum Hinzufügen eines Freundes zur Gruppe (Port von `AddGroupMemberDialog.vue`). */
@Component({
  selector: 'app-add-group-member-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="add-member-title">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content">
            <div class="modal-header">
              <span class="icon-tile me-3" aria-hidden="true"><i class="bi bi-person-plus fs-4"></i></span>
              <h2 class="modal-title fs-5 flex-grow-1" id="add-member-title">Mitglied hinzufügen</h2>
              <button type="button" class="btn-close" aria-label="Schließen" (click)="close()"></button>
            </div>
            <div class="modal-body d-flex flex-column gap-4">
              <div>
                <label for="add-member-search" class="form-label fw-medium">Freund suchen</label>
                <div class="input-group">
                  <span class="input-group-text"><i class="bi bi-search" aria-hidden="true"></i></span>
                  <input
                    id="add-member-search"
                    type="text"
                    class="form-control"
                    placeholder="Name eingeben..."
                    [value]="query()"
                    (input)="search($any($event.target).value)"
                  />
                </div>
                <p class="small text-body-secondary mt-2 mb-0">Du kannst nur Freunde zur Gruppe hinzufügen.</p>
              </div>

              @if (query().length >= minQueryLength) {
                <div>
                  <h3 class="small fw-semibold text-uppercase text-body-secondary mb-3">Suchergebnisse</h3>
                  @if (searching()) {
                    <div class="text-center py-4 text-body-secondary">
                      <span class="spinner-border text-primary" aria-hidden="true"></span>
                      <p class="mt-2 mb-0 small">Wird gesucht...</p>
                    </div>
                  } @else if (filteredResults().length === 0) {
                    <div class="text-center py-4 text-body-secondary">
                      <i class="bi bi-search fs-3 d-block mb-2" aria-hidden="true"></i>
                      <p class="mb-0 small">Keine Freunde gefunden</p>
                    </div>
                  } @else {
                    <div class="list-group result-list">
                      @for (result of filteredResults(); track result.user_id) {
                        <button
                          type="button"
                          class="list-group-item list-group-item-action d-flex align-items-center gap-3"
                          data-testid="result"
                          [class.active]="selected()?.user_id === result.user_id"
                          (click)="selected.set(result)"
                        >
                          <span class="avatar flex-shrink-0" aria-hidden="true">{{ result.name.charAt(0).toUpperCase() || '?' }}</span>
                          <span class="min-w-0">
                            <span class="d-block fw-medium text-truncate">{{ result.name }}</span>
                            @if (result.email) {
                              <span class="d-block small text-truncate opacity-75">{{ result.email }}</span>
                            }
                          </span>
                        </button>
                      }
                    </div>
                  }
                </div>
              }

              @if (selected(); as person) {
                <div class="alert alert-primary d-flex align-items-center justify-content-between mb-0">
                  <div class="d-flex align-items-center gap-3">
                    <span class="avatar" aria-hidden="true">{{ person.name.charAt(0).toUpperCase() || '?' }}</span>
                    <div>
                      <p class="fw-medium mb-0">{{ person.name }}</p>
                      <p class="small mb-0">Wird zur Gruppe hinzugefügt</p>
                    </div>
                  </div>
                  <button type="button" class="btn-close" aria-label="Auswahl aufheben" (click)="selected.set(null)"></button>
                </div>
              }
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="close()">Abbrechen</button>
              <button type="button" class="btn btn-gradient" [disabled]="!selected()" (click)="add()">
                <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>Hinzufügen
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
  styles: '.result-list { max-height: 12rem; overflow-y: auto; }',
})
export class AddGroupMemberDialogComponent {
  private readonly friendships = inject(FriendshipStore);

  readonly visible = input.required<boolean>();
  /** Bereits in der Gruppe; diese Personen erscheinen nicht in den Ergebnissen. */
  readonly existingMemberIds = input<number[]>([]);
  readonly closed = output<void>();
  readonly added = output<number>();

  protected readonly minQueryLength = MIN_QUERY_LENGTH;
  protected readonly query = signal('');
  protected readonly results = signal<Friend[]>([]);
  protected readonly selected = signal<Friend | null>(null);
  protected readonly searching = signal(false);
  protected readonly filteredResults = computed(() => this.results().filter((f) => !this.existingMemberIds().includes(f.user_id)));

  private friends: Friend[] | null = null;

  constructor() {
    effect(() => {
      if (!this.visible()) untracked(() => this.reset());
    });
  }

  /** Filtert die (einmal geladene) Freundesliste ab zwei Zeichen. */
  protected async search(query: string): Promise<void> {
    this.query.set(query);
    if (query.length < MIN_QUERY_LENGTH) {
      this.results.set([]);
      return;
    }
    this.searching.set(true);
    try {
      this.friends ??= await this.friendships.fetchFriends();
      // Wurde inzwischen weitergetippt, gehört das Ergebnis zur neueren Eingabe.
      if (this.query() !== query) return;
      const needle = query.toLowerCase();
      this.results.set(this.friends.filter((f) => f.name.toLowerCase().includes(needle)));
    } catch {
      this.results.set([]);
    } finally {
      if (this.query() === query) this.searching.set(false);
    }
  }

  protected add(): void {
    const person = this.selected();
    if (!person) return;
    this.added.emit(person.user_id);
    this.close();
  }

  @HostListener('document:keydown.escape')
  protected close(): void {
    if (!this.visible()) return;
    this.reset();
    this.closed.emit();
  }

  private reset(): void {
    this.query.set('');
    this.results.set([]);
    this.selected.set(null);
    this.searching.set(false);
    this.friends = null;
  }
}
