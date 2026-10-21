import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BrowserType, browserOrder, detectBrowser } from './detect-browser';

/** `/public/install` (Port von `pages/public/install.vue`): Installationsanleitung je Browser, der erkannte zuerst. */
@Component({
  selector: 'app-install',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './install.component.html',
})
export class InstallComponent {
  protected readonly detected: BrowserType = detectBrowser(navigator.userAgent);
  protected readonly order = browserOrder(this.detected);
  protected readonly origin = location.origin;
  protected readonly host = location.host;
}
