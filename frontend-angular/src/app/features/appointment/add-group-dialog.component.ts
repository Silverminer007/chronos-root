import { ChangeDetectionStrategy, Component, HostListener, effect, inject, input, output, signal, untracked } from '@angular/core';
import { Role } from '../../core/models';
import { GroupStore, GroupSearchResult } from '../groups/group.store';
import { roleLabel } from './participation';
import { RolePickerComponent } from './role-picker.component';

/** Dialog zum Hinzufügen einer Gruppe mit Rolle für alle Mitglieder (Teil von `GroupParticipantsCard.vue`). */
@Component({
  selector: 'app-add-group-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RolePickerComponent],
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="add-group-title">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content">
            <div class="modal-header">
              <span class="icon-tile me-3" aria-hidden="true"><i class="bi bi-people fs-4"></i></span>
              <h2 class="modal-title fs-5 flex-grow-1" id="add-group-title">Gruppe hinzufügen</h2>
              <button type="button" class="btn-close" aria-label="Schließen" (click)="closed.emit()"></button>
            </div>
            <div class="modal-body d-flex flex-column gap-4">
              <div>
                <label for="add-group-search" class="form-label fw-medium">Gruppe suchen</label>
                <div class="input-group">
                  <span class="input-group-text"><i class="bi bi-search" aria-hidden="true"></i></span>
                  <input
                    id="add-group-search"
                    type="text"
                    class="form-control"
                    placeholder="Gruppenname eingeben..."
                    [value]="query()"
                    (input)="search($any($event.target).value)"
                  />
                </div>
              </div>

              <div>
                <span class="form-label fw-medium d-block">Rolle für Gruppenmitglieder</span>
                <app-role-picker [(value)]="role" />
              </div>

              @if (query().length > 0) {
                <div>
                  <h3 class="small fw-semibold text-uppercase text-body-secondary mb-3">Suchergebnisse</h3>
                  @if (searching()) {
                    <div class="text-center py-4 text-body-secondary">
                      <span class="spinner-border text-primary" aria-hidden="true"></span>
                      <p class="mt-2 mb-0">Wird gesucht...</p>
                    </div>
                  } @else if (results().length === 0) {
                    <div class="text-center py-4 text-body-secondary">
                      <i class="bi bi-search fs-3 d-block mb-2" aria-hidden="true"></i>
                      <p class="mb-0">Keine Gruppen gefunden</p>
                    </div>
                  } @else {
                    <div class="list-group result-list">
                      @for (result of results(); track result.id) {
                        <button
                          type="button"
                          class="list-group-item list-group-item-action d-flex align-items-center gap-3"
                          data-testid="result"
                          [class.active]="selected()?.id === result.id"
                          (click)="selected.set(result)"
                        >
                          <span class="avatar flex-shrink-0" aria-hidden="true"><i class="bi bi-people"></i></span>
                          <span class="min-w-0">
                            <span class="d-block fw-medium text-truncate">{{ result.name }}</span>
                            @if (result.member_count) {
                              <span class="d-block small opacity-75">{{ result.member_count }} Mitglieder</span>
                            }
                          </span>
                        </button>
                      }
                    </div>
                  }
                </div>
              }

              @if (selected(); as group) {
                <div class="alert alert-primary d-flex align-items-center justify-content-between mb-0">
                  <div class="d-flex align-items-center gap-3">
                    <span class="avatar" aria-hidden="true"><i class="bi bi-people"></i></span>
                    <div>
                      <p class="fw-medium mb-0">{{ group.name }}</p>
                      <p class="small mb-0">Als {{ label(role()) }}</p>
                    </div>
                  </div>
                  <button type="button" class="btn-close" aria-label="Auswahl aufheben" (click)="selected.set(null)"></button>
                </div>
              }
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" [disabled]="adding()" (click)="closed.emit()">Abbrechen</button>
              <button type="button" class="btn btn-gradient" [disabled]="!selected() || adding()" (click)="add()">
                @if (adding()) {
                  <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
                } @else {
                  <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
                }
                Hinzufügen
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
  styles: '.result-list { max-height: 12rem; overflow-y: auto; }',
})
export class AddGroupDialogComponent {
  private readonly groupStore = inject(GroupStore);

  readonly visible = input.required<boolean>();
  /** Das Hinzufügen läuft (Eltern setzen es, Buttons werden gesperrt). */
  readonly adding = input(false);
  readonly closed = output<void>();
  readonly added = output<{ id: number; name: string; role: Role }>();

  protected readonly query = signal('');
  protected readonly role = signal<Role>('ATTENDANT');
  protected readonly results = signal<GroupSearchResult[]>([]);
  protected readonly selected = signal<GroupSearchResult | null>(null);
  protected readonly searching = signal(false);
  protected readonly label = roleLabel;

  constructor() {
    effect(() => {
      if (!this.visible()) untracked(() => this.reset());
    });
  }

  protected async search(query: string): Promise<void> {
    this.query.set(query);
    this.searching.set(true);
    try {
      const results = await this.groupStore.searchGroups(query);
      // Wurde inzwischen weitergetippt, gehört das Ergebnis zur neueren Eingabe.
      if (this.query() === query) this.results.set(results);
    } catch {
      if (this.query() === query) this.results.set([]);
    } finally {
      if (this.query() === query) this.searching.set(false);
    }
  }

  protected add(): void {
    const group = this.selected();
    if (group) this.added.emit({ id: group.id, name: group.name, role: this.role() });
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.visible() && !this.adding()) this.closed.emit();
  }

  private reset(): void {
    this.query.set('');
    this.role.set('ATTENDANT');
    this.results.set([]);
    this.selected.set(null);
    this.searching.set(false);
  }
}
