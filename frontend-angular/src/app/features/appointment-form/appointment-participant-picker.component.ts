import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { Friend, Group, Role } from '../../core/models';
import { ParticipantLookupService } from '../appointment/participant-lookup.service';
import { FriendshipStore } from '../friends/friendship.store';

export type ParticipationRole = Exclude<Role, 'NONE'>;

export interface SelectedUser {
  id: number;
  name: string;
  role: ParticipationRole;
}

export interface SelectedGroup extends SelectedUser {
  memberCount: number;
}

export interface PickerChange {
  userCount: number;
  groupMemberCount: number;
}

type Tab = 'users' | 'groups';

const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 250;
const DEFAULT_ROLE: ParticipationRole = 'ATTENDANT';
const ROLE_CYCLE: ParticipationRole[] = ['ATTENDANT', 'RESPONSIBLE', 'HELPER', 'GUEST'];
const ROLE_META: Record<ParticipationRole, { short: string; css: string }> = {
  ATTENDANT: { short: 'TN', css: 'text-bg-primary' },
  RESPONSIBLE: { short: 'Org', css: 'text-bg-info' },
  HELPER: { short: 'Helf', css: 'text-bg-success' },
  GUEST: { short: 'Gast', css: 'text-bg-secondary' },
};

const nextRole = (role: ParticipationRole): ParticipationRole =>
  ROLE_CYCLE[(ROLE_CYCLE.indexOf(role) + 1) % ROLE_CYCLE.length];

/** Auswahl von Personen und Gruppen für einen neuen Termin (Port von `AppointmentParticipantPicker.vue`). */
@Component({
  selector: 'app-appointment-participant-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex flex-column gap-3">
      <div class="nav nav-pills nav-fill bg-body-secondary rounded-3 p-1" role="tablist">
        <button
          type="button"
          class="nav-link"
          role="tab"
          [class.active]="tab() === 'users'"
          [attr.aria-selected]="tab() === 'users'"
          (click)="setTab('users')"
        >
          <i class="bi bi-person me-1" aria-hidden="true"></i>Personen
        </button>
        <button
          type="button"
          class="nav-link"
          role="tab"
          [class.active]="tab() === 'groups'"
          [attr.aria-selected]="tab() === 'groups'"
          (click)="setTab('groups')"
        >
          <i class="bi bi-people me-1" aria-hidden="true"></i>Gruppen
        </button>
      </div>

      <div class="input-group">
        <span class="input-group-text"><i class="bi bi-search" aria-hidden="true"></i></span>
        <input
          type="text"
          class="form-control"
          [attr.aria-label]="tab() === 'users' ? 'Freund suchen' : 'Gruppe suchen'"
          [placeholder]="tab() === 'users' ? 'Freund suchen…' : 'Gruppe suchen…'"
          [value]="query()"
          (input)="onInput($any($event.target).value)"
        />
        @if (loading()) {
          <span class="input-group-text"
            ><span class="spinner-border spinner-border-sm text-primary" aria-hidden="true"></span
          ></span>
        }
      </div>

      @if (hasResults()) {
        <div class="list-group results">
          @if (tab() === 'users') {
            @for (friend of userResults(); track friend.user_id) {
              <button
                type="button"
                class="list-group-item list-group-item-action d-flex align-items-center gap-3"
                data-testid="result"
                [disabled]="isUserSelected(friend.user_id)"
                (click)="selectUser(friend)"
              >
                <span class="avatar flex-shrink-0" aria-hidden="true"
                  ><i class="bi bi-person"></i
                ></span>
                <span class="min-w-0 flex-grow-1">
                  <span class="d-block fw-medium text-truncate">{{ friend.name }}</span>
                  @if (friend.email) {
                    <span class="d-block small text-body-secondary text-truncate">{{
                      friend.email
                    }}</span>
                  }
                </span>
                @if (isUserSelected(friend.user_id)) {
                  <i class="bi bi-check-lg text-primary" aria-hidden="true"></i>
                }
              </button>
            }
          } @else {
            @for (group of groupResults(); track group.id) {
              <button
                type="button"
                class="list-group-item list-group-item-action d-flex align-items-center gap-3"
                data-testid="result"
                [disabled]="isGroupSelected(group.id)"
                (click)="selectGroup(group)"
              >
                <span class="avatar flex-shrink-0" aria-hidden="true"
                  ><i class="bi bi-people"></i
                ></span>
                <span class="min-w-0 flex-grow-1">
                  <span class="d-block fw-medium text-truncate">{{ group.name }}</span>
                  <span class="d-block small text-body-secondary"
                    >{{ memberCount(group) }} Mitglieder</span
                  >
                </span>
                @if (isGroupSelected(group.id)) {
                  <i class="bi bi-check-lg text-primary" aria-hidden="true"></i>
                }
              </button>
            }
          }
        </div>
      } @else if (noResults()) {
        <p class="text-center small text-body-secondary mb-0">Keine Ergebnisse</p>
      }

      @if (selectedUsers().length > 0 || selectedGroups().length > 0) {
        <div class="d-flex flex-wrap gap-2">
          @for (user of selectedUsers(); track user.id) {
            <span class="chip" data-testid="chip-user">
              <i class="bi bi-person" aria-hidden="true"></i>
              <span class="chip-name text-truncate">{{ user.name }}</span>
              <button
                type="button"
                class="badge rounded-pill border-0 {{ meta[user.role].css }}"
                data-testid="role"
                title="Tippen zum Wechseln"
                (click)="cycleUserRole(user.id)"
              >
                {{ meta[user.role].short }}
              </button>
              <button
                type="button"
                class="btn-close btn-close-sm"
                data-testid="remove"
                [attr.aria-label]="user.name + ' entfernen'"
                (click)="removeUser(user.id)"
              ></button>
            </span>
          }
          @for (group of selectedGroups(); track group.id) {
            <span class="chip chip-group" data-testid="chip-group">
              <i class="bi bi-people" aria-hidden="true"></i>
              <span class="chip-name text-truncate">{{ group.name }}</span>
              <button
                type="button"
                class="badge rounded-pill border-0 {{ meta[group.role].css }}"
                data-testid="role"
                title="Tippen zum Wechseln"
                (click)="cycleGroupRole(group.id)"
              >
                {{ meta[group.role].short }}
              </button>
              <button
                type="button"
                class="btn-close btn-close-sm"
                data-testid="remove"
                [attr.aria-label]="group.name + ' entfernen'"
                (click)="removeGroup(group.id)"
              ></button>
            </span>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .results {
      max-height: 11rem;
      overflow-y: auto;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.25rem 0.375rem 0.25rem 0.625rem;
      border: 1px solid var(--bs-border-color);
      border-radius: 50rem;
      background: var(--bs-body-bg);
      font-size: 0.75rem;
      font-weight: 500;
    }
    .chip-group {
      background: var(--bs-info-bg-subtle);
      border-color: var(--bs-info-border-subtle);
    }
    .chip-name {
      max-width: 90px;
    }
    .btn-close-sm {
      font-size: 0.5rem;
    }
  `,
})
export class AppointmentParticipantPickerComponent {
  private readonly lookup = inject(ParticipantLookupService);
  private readonly friendships = inject(FriendshipStore);

  readonly changed = output<PickerChange>();

  protected readonly meta = ROLE_META;
  protected readonly tab = signal<Tab>('users');
  protected readonly query = signal('');
  protected readonly loading = signal(false);
  protected readonly userResults = signal<Friend[]>([]);
  protected readonly groupResults = signal<Group[]>([]);
  readonly selectedUsers = signal<SelectedUser[]>([]);
  readonly selectedGroups = signal<SelectedGroup[]>([]);

  protected readonly hasResults = computed(
    () =>
      this.query().length >= MIN_QUERY_LENGTH &&
      (this.tab() === 'users' ? this.userResults() : this.groupResults()).length > 0,
  );
  protected readonly noResults = computed(
    () => this.query().length >= MIN_QUERY_LENGTH && !this.loading() && !this.hasResults(),
  );

  private debounce: ReturnType<typeof setTimeout> | undefined;
  private latestSearch = 0;
  /** Freundesliste, einmal je Picker geladen und lokal gefiltert (wie `useFriends`). */
  private friends: Friend[] = [];

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.debounce));
  }

  protected setTab(tab: Tab): void {
    if (tab === this.tab()) return;
    this.tab.set(tab);
    this.resetSearch();
  }

  protected onInput(value: string): void {
    this.query.set(value);
    clearTimeout(this.debounce);
    if (value.length < MIN_QUERY_LENGTH) {
      this.userResults.set([]);
      this.groupResults.set([]);
      return;
    }
    this.debounce = setTimeout(() => void this.search(), DEBOUNCE_MS);
  }

  /** Die API liefert bei Suchtreffern nicht immer `members`. */
  protected memberCount(group: Group): number {
    return group.members?.length ?? 0;
  }

  protected isUserSelected(id: number): boolean {
    return this.selectedUsers().some((u) => u.id === id);
  }

  protected isGroupSelected(id: number): boolean {
    return this.selectedGroups().some((g) => g.id === id);
  }

  protected selectUser(friend: Friend): void {
    if (this.isUserSelected(friend.user_id)) return;
    this.selectedUsers.update((list) => [
      ...list,
      { id: friend.user_id, name: friend.name, role: DEFAULT_ROLE },
    ]);
    this.resetSearch();
    this.emitChange();
  }

  protected selectGroup(group: Group): void {
    if (this.isGroupSelected(group.id)) return;
    this.selectedGroups.update((list) => [
      ...list,
      { id: group.id, name: group.name, role: DEFAULT_ROLE, memberCount: this.memberCount(group) },
    ]);
    this.resetSearch();
    this.emitChange();
  }

  protected removeUser(id: number): void {
    this.selectedUsers.update((list) => list.filter((u) => u.id !== id));
    this.emitChange();
  }

  protected removeGroup(id: number): void {
    this.selectedGroups.update((list) => list.filter((g) => g.id !== id));
    this.emitChange();
  }

  protected cycleUserRole(id: number): void {
    this.selectedUsers.update((list) =>
      list.map((u) => (u.id === id ? { ...u, role: nextRole(u.role) } : u)),
    );
  }

  protected cycleGroupRole(id: number): void {
    this.selectedGroups.update((list) =>
      list.map((g) => (g.id === id ? { ...g, role: nextRole(g.role) } : g)),
    );
  }

  private async search(): Promise<void> {
    const request = ++this.latestSearch;
    const query = this.query();
    this.loading.set(true);
    try {
      if (this.tab() === 'users') {
        if (this.friends.length < 1) this.friends = await this.friendships.fetchFriends();
        const needle = query.toLowerCase();
        if (request === this.latestSearch)
          this.userResults.set(this.friends.filter((f) => f.name.toLowerCase().includes(needle)));
      } else {
        const groups = await this.lookup.searchGroups(query);
        if (request === this.latestSearch)
          this.groupResults.set(Array.isArray(groups) ? groups : []);
      }
    } catch {
      if (request === this.latestSearch) {
        this.userResults.set([]);
        this.groupResults.set([]);
      }
    } finally {
      if (request === this.latestSearch) this.loading.set(false);
    }
  }

  private resetSearch(): void {
    clearTimeout(this.debounce);
    this.latestSearch++;
    this.loading.set(false);
    this.query.set('');
    this.userResults.set([]);
    this.groupResults.set([]);
  }

  private emitChange(): void {
    this.changed.emit({
      userCount: this.selectedUsers().length,
      groupMemberCount: this.selectedGroups().reduce((sum, g) => sum + g.memberCount, 0),
    });
  }
}
