import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { formatRelativeTime } from '../../core/date-format';
import { getErrorMessage } from '../../core/errors';
import { SearchHeaderComponent } from '../agenda/search-header.component';
import { PushLogEntry } from './admin.models';
import { AdminService } from './admin.service';

type StatusFilter = 'all' | 'success' | 'error';

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: 'Alle' },
  { value: 'success', label: 'Erfolgreich' },
  { value: 'error', label: 'Fehlgeschlagen' },
];

/** Titel und Text einer Push-Nutzlast; Nicht-JSON gilt als reiner Text. */
export function parsePayload(payload: string): { title: string; body: string } {
  try {
    const parsed = JSON.parse(payload);
    return { title: parsed.title ?? '', body: parsed.body ?? '' };
  } catch {
    return { title: '', body: payload };
  }
}

/** Push-Protokoll `/admin/push` mit Filtern (Port von `pages/admin/push.vue`). */
@Component({
  selector: 'app-admin-push',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, RouterLink, FormsModule],
  templateUrl: './admin-push.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminPushComponent implements OnInit {
  private readonly admin = inject(AdminService);

  protected readonly formatRelativeTime = formatRelativeTime;
  protected readonly parsePayload = parsePayload;
  protected readonly statusFilters = STATUS_FILTERS;

  protected readonly entries = signal<PushLogEntry[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly filterUserId = signal('');
  protected readonly filterType = signal('');
  protected readonly filterStatus = signal<StatusFilter>('all');
  /** ISO-Datum (`JJJJ-MM-TT`) aus dem Datumsfeld. */
  protected readonly filterFrom = signal('');
  protected readonly filterTo = signal('');

  protected readonly filtered = computed(() => {
    const userId = this.filterUserId();
    const type = this.filterType();
    const status = this.filterStatus();
    const from = this.filterFrom() ? new Date(`${this.filterFrom()}T00:00:00`) : null;
    const to = this.filterTo() ? new Date(`${this.filterTo()}T23:59:59.999`) : null;
    return this.entries().filter((entry) => {
      if (userId && entry.user_id !== Number(userId)) return false;
      if (type && entry.notification_type !== type) return false;
      if (status === 'success' && !entry.success) return false;
      if (status === 'error' && entry.success) return false;
      const created = new Date(entry.created_at);
      return !(from && created < from) && !(to && created > to);
    });
  });

  async ngOnInit(): Promise<void> {
    try {
      this.entries.set((await this.admin.pushLog()) ?? []);
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Das Protokoll konnte nicht geladen werden.'));
    } finally {
      this.loading.set(false);
    }
  }
}
