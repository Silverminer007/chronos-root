import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Rahmen der angemeldeten Seiten (Port von `layouts/default.vue`): nur der Router-Outlet.
 * Die Dialoge (PWA-Installation, Push, Neuigkeiten) folgen mit F-11, jede Seite hat ihren eigenen Header.
 */
@Component({
  selector: 'app-default-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class DefaultLayoutComponent {}
