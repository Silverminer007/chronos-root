import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';
import { CreateGroupDialogComponent } from './create-group-dialog.component';
import { GroupCardComponent } from './group-card.component';
import { GroupStore } from './group.store';

/** Seite `/groups` mit der Gruppenliste (Port von `pages/groups/index.vue`). */
@Component({
  selector: 'app-groups',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, GroupCardComponent, CreateGroupDialogComponent],
  templateUrl: './groups.component.html',
  styleUrl: './groups.component.scss',
})
export class GroupsComponent implements OnInit {
  protected readonly store = inject(GroupStore);
  private readonly toast = inject(ToastService);

  protected readonly showCreateDialog = signal(false);

  ngOnInit(): void {
    // Fehler setzt der Store als Meldung, die Seite zeigt sie an.
    this.store.fetchGroups().catch(() => undefined);
  }

  protected async deleteGroup(groupId: number): Promise<void> {
    try {
      await this.store.deleteGroup(groupId);
      this.toast.success('Gruppe gelöscht', 'Die Gruppe wurde gelöscht');
    } catch {
      this.toast.error('Fehler', 'Gruppe konnte nicht gelöscht werden');
    }
  }
}
