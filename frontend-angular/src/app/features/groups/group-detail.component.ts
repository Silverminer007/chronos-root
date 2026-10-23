import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ModalService } from '../../core/modal.service';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';
import { AddGroupMemberDialogComponent } from './add-group-member-dialog.component';
import { GroupMemberCardComponent } from './group-member-card.component';
import { GroupStore } from './group.store';

const GROUPS_URL = '/groups';

/** Seite `/groups/:groupId` mit Mitgliedern und Aktionen (Port von `pages/groups/[groupId].vue`). */
@Component({
  selector: 'app-group-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, GroupMemberCardComponent, AddGroupMemberDialogComponent],
  templateUrl: './group-detail.component.html',
  styleUrl: './group-detail.component.scss',
})
export class GroupDetailComponent {
  protected readonly store = inject(GroupStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly modal = inject(ModalService);

  private groupId = 0;

  protected readonly error = signal('');
  protected readonly showAddMemberDialog = signal(false);
  protected readonly group = this.store.currentGroup;
  protected readonly currentUserId = computed(() => this.auth.user()?.id);
  protected readonly isLastMember = computed(() => this.group()?.members?.length === 1);
  protected readonly existingMemberIds = computed(() => this.group()?.members?.map((m) => m.id) ?? []);

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(takeUntilDestroyed())
      .subscribe((params) => void this.load(Number(params.get('groupId'))));
    inject(DestroyRef).onDestroy(() => this.store.clearCurrentGroup());
  }

  protected goBack(): void {
    void this.router.navigateByUrl(GROUPS_URL);
  }

  protected async addMember(userId: number): Promise<void> {
    try {
      await this.store.addMember(this.groupId, userId);
      this.toast.success('Mitglied hinzugefügt', 'Das Mitglied wurde zur Gruppe hinzugefügt');
    } catch {
      this.toast.error('Fehler', 'Mitglied konnte nicht hinzugefügt werden');
    }
  }

  protected async removeMember(userId: number): Promise<void> {
    try {
      await this.store.removeMember(this.groupId, userId);
      this.toast.success('Mitglied entfernt', 'Das Mitglied wurde aus der Gruppe entfernt');
    } catch {
      this.toast.error('Fehler', 'Mitglied konnte nicht entfernt werden');
    }
  }

  protected async leaveGroup(): Promise<void> {
    const confirmed = await this.modal.confirm({
      title: 'Gruppe verlassen',
      message: 'Möchtest du diese Gruppe wirklich verlassen?',
      confirmText: 'Verlassen',
      confirmColor: 'purple',
    });
    const userId = this.currentUserId();
    if (!confirmed || !userId) return;
    try {
      await this.store.leaveGroup(this.groupId, userId);
      this.toast.info('Gruppe verlassen', 'Du hast die Gruppe verlassen');
      this.goBack();
    } catch {
      this.toast.error('Fehler', 'Gruppe konnte nicht verlassen werden');
    }
  }

  protected async deleteGroup(): Promise<void> {
    const confirmed = await this.modal.confirm({
      title: 'Gruppe löschen',
      message: 'Du bist das letzte Mitglied. Wenn du gehst, wird die Gruppe gelöscht. Fortfahren?',
      confirmText: 'Löschen',
      confirmColor: 'red',
    });
    if (!confirmed) return;
    try {
      await this.store.deleteGroup(this.groupId);
      this.toast.success('Gruppe gelöscht', 'Die Gruppe wurde gelöscht');
      this.goBack();
    } catch {
      this.toast.error('Fehler', 'Gruppe konnte nicht gelöscht werden');
    }
  }

  private async load(groupId: number): Promise<void> {
    this.groupId = groupId;
    this.error.set('');
    try {
      if (!(await this.store.fetchGroup(groupId))) this.error.set('Gruppe nicht gefunden');
    } catch {
      this.error.set('Gruppe konnte nicht geladen werden');
    }
  }
}
