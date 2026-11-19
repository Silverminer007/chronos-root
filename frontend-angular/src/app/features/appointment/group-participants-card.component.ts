import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { Appointment, ParticipationStatus, Role } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { AddGroupDialogComponent } from './add-group-dialog.component';
import { isResponsible, sortByStatusThenName } from './participation';
import { IconComponent } from '../../shared/icon/icon.component';

const STATUS_ICON: Record<ParticipationStatus, { icon: string; css: string }> = {
  APPROVED: { icon: 'check', css: 'text-success' },
  REJECTED: { icon: 'x', css: 'text-danger' },
  PENDING: { icon: 'clock', css: 'text-warning' },
};

/** Eingeladene Gruppen mit aufklappbaren Mitgliedern und Hinzufügen (Port von `GroupParticipantsCard.vue`). */
@Component({
  selector: 'app-group-participants-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, AddGroupDialogComponent],
  template: `
    <div class="card shadow-sm">
      <div class="card-header p-4 bg-transparent d-flex align-items-center justify-content-between">
        <div class="d-flex align-items-center gap-3">
          <span class="icon-tile" aria-hidden="true"><app-icon name="users" class="fs-5" /></span>
          <div>
            <h3 class="card-heading fw-bold mb-0">Gruppen</h3>
            <p class="small text-body-secondary mb-0">
              {{ groups().length }} {{ groups().length === 1 ? 'Gruppe' : 'Gruppen' }} eingeladen
            </p>
          </div>
        </div>
        @if (responsible()) {
          <button type="button" class="btn btn-gradient btn-sm" title="Gruppe hinzufügen" aria-label="Gruppe hinzufügen" (click)="showAddDialog.set(true)">
            <app-icon name="plus" />
          </button>
        }
      </div>

      @if (groups().length === 0) {
        <div class="card-body text-center p-4">
          <div class="icon-tile rounded-circle empty-icon mb-3" aria-hidden="true"><app-icon name="users" class="fs-3" /></div>
          <p class="text-body-secondary mb-0">Keine Gruppen eingeladen</p>
          <p class="small text-body-tertiary mt-1">Teilnehmer wurden einzeln hinzugefügt</p>
          @if (responsible()) {
            <button type="button" class="btn btn-outline-primary mt-3" (click)="showAddDialog.set(true)">
              <app-icon name="plus" class="me-2" />Gruppe hinzufügen
            </button>
          }
        </div>
      } @else {
        <ul class="list-group list-group-flush">
          @for (group of groups(); track group.id) {
            <li class="list-group-item p-0" data-testid="group">
              <button
                type="button"
                class="btn w-100 text-start d-flex align-items-center justify-content-between gap-3 p-3 rounded-0"
                data-testid="group-toggle"
                [attr.aria-expanded]="isExpanded(group.id)"
                (click)="toggle(group.id)"
              >
                <span class="d-flex align-items-center gap-3">
                  <span class="avatar" aria-hidden="true"><app-icon name="users" /></span>
                  <span>
                    <span class="d-block fw-medium">{{ group.name }}</span>
                    <span class="d-block small text-body-secondary">{{ approved(group.id) }}/{{ members(group.id).length }} zugesagt</span>
                  </span>
                </span>
                <app-icon [name]="isExpanded(group.id) ? 'chevron-up' : 'chevron-down'" />
              </button>
              @if (isExpanded(group.id)) {
                <ul class="list-group list-group-flush bg-body-tertiary border-top">
                  @for (member of members(group.id); track member.user_id) {
                    <li class="list-group-item bg-transparent d-flex align-items-center justify-content-between ps-5 py-2" data-testid="member">
                      <span class="d-flex align-items-center gap-3">
                        <span class="avatar avatar-sm" aria-hidden="true">{{ member.name.charAt(0).toUpperCase() || '?' }}</span>
                        <span class="small">{{ member.name }}</span>
                      </span>
                      <app-icon [name]="statusIcon[member.status].icon" [class]="statusIcon[member.status].css" />
                    </li>
                  }
                </ul>
              }
            </li>
          }
        </ul>
      }
    </div>

    <app-add-group-dialog [visible]="showAddDialog()" [adding]="adding()" (closed)="showAddDialog.set(false)" (added)="add($event)" />
  `,
  styles: '.empty-icon { width: 4rem; height: 4rem; } .avatar-sm { width: 2rem; height: 2rem; font-size: 0.75rem; }',
})
export class GroupParticipantsCardComponent {
  private readonly store = inject(AppointmentStore);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly appointment = input.required<Appointment>();

  protected readonly showAddDialog = signal(false);
  protected readonly adding = signal(false);
  protected readonly expanded = signal<ReadonlySet<number>>(new Set());

  protected readonly statusIcon = STATUS_ICON;
  protected readonly responsible = computed(() => isResponsible(this.appointment(), this.auth.user()?.id));
  protected readonly groups = computed(() => this.appointment().group_participants);

  protected members(groupId: number) {
    return sortByStatusThenName(this.appointment().participants.filter((p) => p.via_group_id === groupId));
  }

  protected approved(groupId: number): number {
    return this.members(groupId).filter((p) => p.status === 'APPROVED').length;
  }

  protected isExpanded(groupId: number): boolean {
    return this.expanded().has(groupId);
  }

  protected toggle(groupId: number): void {
    this.expanded.update((set) => {
      const next = new Set(set);
      if (!next.delete(groupId)) next.add(groupId);
      return next;
    });
  }

  protected async add(data: { id: number; name: string; role: Role }): Promise<void> {
    this.adding.set(true);
    try {
      await this.store.addGroupParticipant(this.appointment().id, data.id, data.role);
      this.toast.success('Gruppe hinzugefügt', `"${data.name}" wurde zum Termin hinzugefügt`);
      this.showAddDialog.set(false);
    } catch {
      this.toast.error('Fehler', 'Gruppe konnte nicht hinzugefügt werden');
    } finally {
      this.adding.set(false);
    }
  }
}
