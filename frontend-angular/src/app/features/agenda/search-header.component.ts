import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AppointmentStore } from './appointment.store';

interface MenuLink {
  label: string;
  icon: string;
  path: string;
}

/** Kopfzeile der Agenda mit Suchfeld und Benutzermenü (Port von `SearchHeader.vue`). */
@Component({
  selector: 'app-search-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  host: { '(document:click)': 'closeOnOutsideClick($event)' },
  templateUrl: './search-header.component.html',
  styleUrl: './search-header.component.scss',
})
export class SearchHeaderComponent {
  private readonly store = inject(AppointmentStore);
  private readonly auth = inject(AuthService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly user = this.auth.user;
  protected readonly menuOpen = signal(false);
  protected readonly initials = computed(
    () => `${this.user()?.first_name?.charAt(0) ?? ''}${this.user()?.last_name?.charAt(0) ?? ''}`,
  );
  protected readonly links: MenuLink[] = [
    { label: 'Profil', icon: 'bi-person', path: '/profile' },
    { label: 'Freunde', icon: 'bi-people', path: '/friends' },
    { label: 'Gruppen', icon: 'bi-diagram-3', path: '/groups' },
    { label: 'Einstellungen', icon: 'bi-gear', path: '/settings' },
  ];

  protected search(query: string): void {
    // Fehler setzt der Store als Meldung, die Agenda zeigt sie an.
    this.store.search(query).catch(() => undefined);
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected logout(): void {
    this.menuOpen.set(false);
    void this.auth.logout();
  }

  protected closeOnOutsideClick(event: Event): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }
}
