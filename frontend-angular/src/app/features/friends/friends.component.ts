import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';
import { FriendCardComponent } from './friend-card.component';
import { FriendRequestCardComponent } from './friend-request-card.component';
import { FriendshipStore } from './friendship.store';
import { SendFriendRequestDialogComponent } from './send-friend-request-dialog.component';

type Tab = 'friends' | 'requests';

/** Seite `/friends` mit Freundesliste und Anfragen (Port von `pages/friends.vue`). */
@Component({
  selector: 'app-friends',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, FriendCardComponent, FriendRequestCardComponent, SendFriendRequestDialogComponent],
  templateUrl: './friends.component.html',
  styleUrl: './friends.component.scss',
})
export class FriendsComponent implements OnInit {
  protected readonly store = inject(FriendshipStore);
  private readonly toast = inject(ToastService);

  protected readonly activeTab = signal<Tab>('friends');
  protected readonly showAddDialog = signal(false);

  ngOnInit(): void {
    // Fehler setzt der Store als Meldung, die Seite zeigt sie an.
    this.store.fetchAll().catch(() => undefined);
  }

  protected removeFriend(friendId: number): Promise<void> {
    return this.run(() => this.store.endFriendship(friendId), 'success', 'Freundschaft beendet', 'Die Freundschaft wurde beendet', 'Freundschaft konnte nicht beendet werden');
  }

  protected acceptRequest(requestId: number): Promise<void> {
    return this.run(() => this.store.acceptRequest(requestId), 'success', 'Anfrage angenommen', 'Ihr seid jetzt Freunde!', 'Anfrage konnte nicht angenommen werden');
  }

  protected declineRequest(requestId: number): Promise<void> {
    return this.run(() => this.store.declineRequest(requestId), 'info', 'Anfrage abgelehnt', 'Die Anfrage wurde abgelehnt', 'Anfrage konnte nicht abgelehnt werden');
  }

  protected cancelRequest(requestId: number): Promise<void> {
    return this.run(() => this.store.cancelRequest(requestId), 'info', 'Anfrage zurückgezogen', 'Die Anfrage wurde zurückgezogen', 'Anfrage konnte nicht zurückgezogen werden');
  }

  private async run(
    action: () => Promise<void>,
    severity: 'success' | 'info',
    summary: string,
    detail: string,
    failure: string,
  ): Promise<void> {
    try {
      await action();
      this.toast.add({ severity, summary, detail });
    } catch {
      this.toast.error('Fehler', failure);
    }
  }
}
