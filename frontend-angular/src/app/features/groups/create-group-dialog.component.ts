import { ChangeDetectionStrategy, Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { getErrorMessage } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { GroupStore } from './group.store';

const MIN_NAME_LENGTH = 2;

/** Dialog zum Anlegen einer Gruppe (Port von `CreateGroupDialog.vue`). */
@Component({
  selector: 'app-create-group-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <div class="modal-backdrop fade show"></div>
      <div class="modal fade show d-block" tabindex="-1" role="dialog" aria-modal="true" aria-labelledby="create-group-title">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <span class="icon-tile me-3" aria-hidden="true"><i class="bi bi-people fs-4"></i></span>
              <h2 class="modal-title fs-5 flex-grow-1" id="create-group-title">Neue Gruppe</h2>
              <button type="button" class="btn-close" aria-label="Schließen" (click)="close()"></button>
            </div>
            <div class="modal-body">
              <label for="group-name" class="form-label fw-medium">Gruppenname</label>
              <div class="input-group">
                <span class="input-group-text"><i class="bi bi-tag" aria-hidden="true"></i></span>
                <input
                  id="group-name"
                  type="text"
                  class="form-control"
                  placeholder="z.B. Leiterrunde, Helfer-Team..."
                  [value]="name()"
                  (input)="name.set($any($event.target).value)"
                  (keyup.enter)="create()"
                />
              </div>
              <p class="small text-body-secondary mt-2 mb-0">Nach dem Erstellen kannst du Mitglieder zur Gruppe hinzufügen.</p>
              @if (error()) {
                <div class="alert alert-danger small mt-3 mb-0" role="alert">{{ error() }}</div>
              }
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="close()">Abbrechen</button>
              <button type="button" class="btn btn-gradient" [disabled]="!valid() || loading()" (click)="create()">
                @if (loading()) {
                  <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
                } @else {
                  <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
                }
                Erstellen
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class CreateGroupDialogComponent {
  private readonly store = inject(GroupStore);
  private readonly toast = inject(ToastService);

  readonly visible = input.required<boolean>();
  readonly closed = output<void>();

  protected readonly name = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly valid = computed(() => this.name().trim().length >= MIN_NAME_LENGTH);

  constructor() {
    effect(() => {
      if (!this.visible()) untracked(() => this.reset());
    });
  }

  protected async create(): Promise<void> {
    if (!this.valid()) {
      this.error.set(`Der Gruppenname muss mindestens ${MIN_NAME_LENGTH} Zeichen lang sein`);
      return;
    }
    const name = this.name().trim();
    this.loading.set(true);
    this.error.set('');
    try {
      await this.store.createGroup(name);
      this.toast.success('Gruppe erstellt', `Die Gruppe "${name}" wurde erstellt`);
      this.close();
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Fehler beim Erstellen der Gruppe'));
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
    this.name.set('');
    this.error.set('');
  }
}
