import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NewFeaturesDialogComponent } from './new-features-dialog.component';
import { PushPermissionModalComponent } from './push-permission-modal.component';
import { PwaInstallDialogComponent } from './pwa-install-dialog.component';

/**
 * Rahmen der angemeldeten Seiten (Port von `layouts/default.vue`): PWA-Installation, Push-Berechtigung und
 * Neuigkeiten als Dialoge über dem Router-Outlet. Jede Seite hat ihren eigenen Header. Die Dialoge liegen in einem
 * `@defer`-Block, damit sie das initiale Bundle nicht vergrößern.
 */
@Component({
  selector: 'app-default-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, PwaInstallDialogComponent, PushPermissionModalComponent, NewFeaturesDialogComponent],
  template: `
    @defer (on immediate) {
      <app-pwa-install-dialog />
      <app-push-permission-modal />
      <app-new-features-dialog />
    }
    <router-outlet />
  `,
})
export class DefaultLayoutComponent {}
