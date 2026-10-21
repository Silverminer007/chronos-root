import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { LandingFooterComponent } from './landing-footer.component';

/** Rahmen der öffentlichen Seiten (Port von `layouts/landingpage.vue` samt dem je Seite wiederholten Kopf und Hintergrund). */
@Component({
  selector: 'app-landing-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterOutlet, LandingFooterComponent],
  templateUrl: './landing-layout.component.html',
  styleUrl: './landing-layout.component.scss',
})
export class LandingLayoutComponent {}
