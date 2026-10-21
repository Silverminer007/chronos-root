import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PublicConfigService } from '../../../core/public-config.service';

/** `/public/datenschutz` (Port von `pages/public/datenschutz.vue`); Verantwortlicher aus der Konfiguration des BFF. */
@Component({
  selector: 'app-datenschutz',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './datenschutz.component.html',
})
export class DatenschutzComponent {
  private readonly config = inject(PublicConfigService);
  protected readonly impressum = this.config.impressum;

  constructor() {
    void this.config.loadImpressum();
  }
}
