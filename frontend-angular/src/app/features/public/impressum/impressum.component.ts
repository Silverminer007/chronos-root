import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PublicConfigService } from '../../../core/public-config.service';

/** `/public/impressum` (Port von `pages/public/impressum.vue`); Angaben aus der Konfiguration des BFF. */
@Component({
  selector: 'app-impressum',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './impressum.component.html',
})
export class ImpressumComponent {
  private readonly config = inject(PublicConfigService);
  protected readonly impressum = this.config.impressum;

  constructor() {
    void this.config.loadImpressum();
  }
}
