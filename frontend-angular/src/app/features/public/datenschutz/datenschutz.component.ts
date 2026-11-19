import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PublicConfigService } from '../../../core/public-config.service';
import { IconComponent } from '../../../shared/icon/icon.component';

/** `/public/datenschutz` (Port von `pages/public/datenschutz.vue`); Verantwortlicher aus der Konfiguration des BFF. */
@Component({
  imports: [IconComponent],
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
