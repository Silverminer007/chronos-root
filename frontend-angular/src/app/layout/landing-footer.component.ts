import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Port von `LandingFooter.vue`. */
@Component({
  selector: 'app-landing-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <footer class="container py-4 text-center text-white-50">
      <p class="mb-2">&copy; {{ year }} Chronos. Terminplanung für Jugendverbände.</p>
      <div class="d-flex justify-content-center gap-3 small">
        <a routerLink="/public/impressum" class="link-light link-opacity-75 link-opacity-100-hover">Impressum</a>
        <span aria-hidden="true">|</span>
        <a routerLink="/public/datenschutz" class="link-light link-opacity-75 link-opacity-100-hover">Datenschutz</a>
      </div>
    </footer>
  `,
})
export class LandingFooterComponent {
  protected readonly year = new Date().getUTCFullYear();
}
