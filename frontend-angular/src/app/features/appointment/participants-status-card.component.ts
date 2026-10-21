import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { Appointment, Role, UserParticipant } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { AddParticipantDialogComponent } from './add-participant-dialog.component';
import { ASSIGNABLE_ROLES, isResponsible, roleLabel, sortByRoleThenStatusThenName, statusLabel } from './participation';

interface OpenMenu {
  participant: UserParticipant;
  top: number;
  right: number;
}

const ROLE_BADGE: Record<string, string> = {
  RESPONSIBLE: 'bg-primary-subtle text-primary-emphasis',
  ATTENDANT: 'bg-info-subtle text-info-emphasis',
  HELPER: 'bg-success-subtle text-success-emphasis',
  GUEST: 'bg-secondary-subtle text-secondary-emphasis',
};

const STATUS_BADGE: Record<string, { css: string; icon: string }> = {
  APPROVED: { css: 'bg-success-subtle text-success-emphasis', icon: 'bi-check-lg' },
  REJECTED: { css: 'bg-danger-subtle text-danger-emphasis', icon: 'bi-x-lg' },
  PENDING: { css: 'bg-warning-subtle text-warning-emphasis', icon: 'bi-clock' },
};

/** Teilnehmerliste mit Fortschritt, Rollenmenü und Hinzufügen (Port von `ParticipantsStatusCard.vue`). */
@Component({
  selector: 'app-participants-status-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AddParticipantDialogComponent],
  host: { '(document:click)': 'closeMenu()', '(window:resize)': 'closeMenu()' },
  template: `
    <div class="card shadow-sm">
      <div class="card-header p-4 bg-transparent">
        <div class="d-flex align-items-center justify-content-between">
          <h3 class="h5 fw-bold mb-0">Teilnehmer</h3>
          @if (responsible()) {
            <button type="button" class="btn btn-gradient btn-sm" aria-label="Teilnehmer hinzufügen" (click)="showAddDialog.set(true)">
              <i class="bi bi-plus-lg" aria-hidden="true"></i>
            </button>
          }
        </div>
        <p class="small text-body-secondary mt-1 mb-0">{{ approvedCount() }} / {{ participants().length }} zugesagt</p>
        <div class="progress mt-3" role="progressbar" aria-label="Zusagen" [attr.aria-valuenow]="approvedCount()" aria-valuemin="0" [attr.aria-valuemax]="progressMax()">
          <div class="progress-bar bg-gradient-brand" [style.width.%]="progress()"></div>
        </div>
      </div>

      <ul class="list-group list-group-flush participant-list" (scroll)="closeMenu()">
        @for (p of participants(); track p.user_id) {
          <li class="list-group-item p-3" data-testid="participant">
            <div class="d-flex align-items-start gap-3">
              <span class="avatar flex-shrink-0" aria-hidden="true">{{ p.name.charAt(0).toUpperCase() || '?' }}</span>
              <div class="flex-grow-1 min-w-0">
                <p class="fw-medium mb-1 text-truncate" data-testid="name">{{ p.name }}</p>
                <div class="d-flex flex-wrap align-items-center gap-2">
                  @if (p.role && p.role !== 'NONE') {
                    <span class="badge {{ roleBadge(p.role) }}">{{ label(p.role) }}</span>
                  }
                  @if (p.via_group_name) {
                    <span class="badge bg-primary-subtle text-primary-emphasis"><i class="bi bi-people me-1" aria-hidden="true"></i>{{ p.via_group_name }}</span>
                  }
                  <span class="badge {{ statusBadge(p.status).css }}">
                    <i class="bi {{ statusBadge(p.status).icon }} me-1" aria-hidden="true"></i>{{ statusText(p.status) }}
                  </span>
                </div>
              </div>
              @if (responsible() && p.role !== 'RESPONSIBLE') {
                <button type="button" class="btn btn-outline-secondary border-0 btn-sm flex-shrink-0" [attr.aria-label]="'Aktionen für ' + p.name" aria-haspopup="menu" (click)="toggleMenu($event, p)">
                  <i class="bi bi-three-dots-vertical" aria-hidden="true"></i>
                </button>
              }
            </div>
          </li>
        }
      </ul>
    </div>

    @if (menu(); as m) {
      <ul class="dropdown-menu show position-fixed shadow" role="menu" [style.top.px]="m.top" [style.right.px]="m.right">
        <li><h6 class="dropdown-header">Rolle ändern</h6></li>
        @for (role of rolesFor(m.participant); track role) {
          <li><button type="button" class="dropdown-item" role="menuitem" (click)="changeRole(m.participant, role)">{{ label(role) }}</button></li>
        }
        <li><hr class="dropdown-divider" /></li>
        <li>
          <button type="button" class="dropdown-item text-danger" role="menuitem" (click)="remove(m.participant)">
            <i class="bi bi-trash me-2" aria-hidden="true"></i>Entfernen
          </button>
        </li>
      </ul>
    }

    <app-add-participant-dialog [visible]="showAddDialog()" (closed)="showAddDialog.set(false)" (added)="add($event)" />
  `,
  styles: '.participant-list { max-height: 600px; overflow-y: auto; }',
})
export class ParticipantsStatusCardComponent {
  private readonly store = inject(AppointmentStore);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();

  protected readonly showAddDialog = signal(false);
  protected readonly menu = signal<OpenMenu | null>(null);

  protected readonly responsible = computed(() => isResponsible(this.appointment(), this.auth.user()?.id));
  protected readonly participants = computed(() => sortByRoleThenStatusThenName(this.appointment().participants));
  protected readonly approvedCount = computed(() => this.participants().filter((p) => p.status === 'APPROVED').length);
  protected readonly progressMax = computed(() => Math.max(this.appointment().minimal_attendees, this.participants().length, 1));
  protected readonly progress = computed(() => (this.approvedCount() / this.progressMax()) * 100);

  protected readonly label = roleLabel;
  protected readonly statusText = statusLabel;
  protected readonly roleBadge = (role: Role) => ROLE_BADGE[role] ?? ROLE_BADGE['GUEST'];
  protected readonly statusBadge = (status: string) => STATUS_BADGE[status] ?? STATUS_BADGE['PENDING'];
  protected readonly rolesFor = (p: UserParticipant) => ASSIGNABLE_ROLES.filter((r) => r !== p.role);

  /** Das Menü hängt außerhalb der scrollenden Liste und wird unter dem Button positioniert. */
  protected toggleMenu(event: Event, participant: UserParticipant): void {
    event.stopPropagation();
    if (this.menu()?.participant.user_id === participant.user_id) {
      this.menu.set(null);
      return;
    }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.menu.set({ participant, top: rect.bottom + 4, right: window.innerWidth - rect.right });
  }

  protected closeMenu(): void {
    if (this.menu()) this.menu.set(null);
  }

  protected async add(data: { id: number; role: Role }): Promise<void> {
    try {
      await this.store.addParticipant(this.appointment().id, data.id, data.role);
      this.toast.success('Teilnehmer hinzugefügt', 'Der Teilnehmer wurde erfolgreich hinzugefügt');
    } catch {
      this.toast.error('Fehler', 'Teilnehmer konnte nicht hinzugefügt werden');
    }
  }

  protected async changeRole(participant: UserParticipant, role: Role): Promise<void> {
    try {
      await this.store.changeParticipantRole(this.appointment().id, participant.user_id, role);
      this.toast.success('Rolle geändert', 'Die Teilnehmerrolle wurde erfolgreich geändert');
    } catch {
      this.toast.error('Fehler', 'Rolle konnte nicht geändert werden');
    }
  }

  protected async remove(participant: UserParticipant): Promise<void> {
    try {
      await this.store.removeParticipant(this.appointment().id, participant.user_id);
      this.toast.success('Teilnehmer entfernt', `${participant.name} wurde entfernt`);
    } catch {
      this.toast.error('Fehler', 'Teilnehmer konnte nicht entfernt werden');
    }
  }
}
