import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatRelativeTime } from '../../core/date-format';
import { getErrorMessage } from '../../core/errors';
import { ModalService } from '../../core/modal.service';
import { ToastService } from '../../core/toast.service';
import { SearchHeaderComponent } from '../agenda/search-header.component';
import { AdminStatistics, AdminUser } from './admin.models';
import { AdminService } from './admin.service';

const RECENT_LOGIN_COUNT = 20;
const AB_VARIANTS = ['a', 'b', 'c'] as const;

type AbVariant = (typeof AB_VARIANTS)[number];

/** Admin-Übersicht `/admin` (Port von `pages/admin/index.vue`); der Zugriff wird nur vom Backend geprüft. */
@Component({
  selector: 'app-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchHeaderComponent, RouterLink, NgTemplateOutlet],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.scss',
})
export class AdminComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly modal = inject(ModalService);
  private readonly toast = inject(ToastService);

  protected readonly formatRelativeTime = formatRelativeTime;
  protected readonly variants = AB_VARIANTS;

  private readonly statistics = signal<AdminStatistics | null>(null);
  protected readonly users = signal<AdminUser[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly surveyLoading = signal(false);

  protected readonly stats = computed(() => {
    const s = this.statistics();
    return [
      { label: 'Registrierte Nutzer', icon: 'bi-people', value: s?.users.total ?? 0 },
      { label: 'Anstehende Termine', icon: 'bi-calendar-event', value: s?.appointments.planned ?? 0 },
      { label: 'Gruppen', icon: 'bi-folder', value: s?.groups.total ?? 0 },
      { label: 'Freundschaften', icon: 'bi-heart', value: s?.friendships.accepted ?? 0 },
      { label: 'Nachrichten', icon: 'bi-chat-square', value: s?.messages.total ?? 0 },
      { label: 'Push-Abonnements', icon: 'bi-bell', value: s?.pushSubscriptions.total ?? 0, link: '/admin/push' },
    ];
  });

  /** Zuweisung der Nutzer zu den Varianten per `id % 3`. */
  protected readonly abGroups = computed(() => {
    const groups: Record<AbVariant, AdminUser[]> = { a: [], b: [], c: [] };
    for (const user of this.users()) groups[AB_VARIANTS[user.id % AB_VARIANTS.length]].push(user);
    return groups;
  });

  async ngOnInit(): Promise<void> {
    try {
      const [statistics, users] = await Promise.all([this.admin.statistics(), this.admin.users(RECENT_LOGIN_COUNT)]);
      this.statistics.set(statistics);
      this.users.set(users.items ?? []);
    } catch (err) {
      this.error.set(getErrorMessage(err, 'Die Daten konnten nicht geladen werden.'));
    }
  }

  protected initials(user: AdminUser): string {
    return user.firstName && user.lastName ? (user.firstName[0] + user.lastName[0]).toUpperCase() : '';
  }

  protected async sendSurveyInvitations(): Promise<void> {
    const confirmed = await this.modal.confirm({
      title: 'Umfrage-Einladungen senden',
      message: 'Es wird eine Push-Benachrichtigung an alle Nutzer gesendet, die noch nicht eingeladen wurden. Fortfahren?',
      confirmText: 'Senden',
      confirmColor: 'purple',
    });
    if (!confirmed) return;
    this.surveyLoading.set(true);
    try {
      await this.admin.sendSurveyInvitations();
      this.toast.add({ severity: 'success', summary: 'Einladungen versendet' });
    } catch {
      this.toast.add({
        severity: 'error',
        summary: 'Fehler beim Versenden',
        detail: 'Bitte versuche es erneut.',
        life: 4000,
      });
    } finally {
      this.surveyLoading.set(false);
    }
  }
}
