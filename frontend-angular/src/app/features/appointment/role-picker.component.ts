import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import { Role } from '../../core/models';
import { ASSIGNABLE_ROLES, roleLabel } from './participation';
import { IconComponent } from '../../shared/icon/icon.component';

const ICONS: Record<string, string> = {
  RESPONSIBLE: 'star',
  ATTENDANT: 'user',
  HELPER: 'wrench',
  GUEST: 'eye',
};

/** Auswahl der Rolle in den Hinzufügen-Dialogen (je Rolle eine Kachel). */
@Component({
  imports: [IconComponent],
  selector: 'app-role-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="row row-cols-2 row-cols-sm-4 g-2" role="radiogroup">
      @for (role of roles; track role) {
        <div class="col">
          <button
            type="button"
            class="btn w-100 py-3 border-2"
            role="radio"
            [class]="value() === role ? 'btn-outline-primary active' : 'btn-outline-secondary'"
            [attr.aria-checked]="value() === role"
            (click)="value.set(role)"
          >
            <app-icon [name]="icons[role]" class="d-block mb-1" />
            <span class="small fw-medium">{{ label(role) }}</span>
          </button>
        </div>
      }
    </div>
  `,
})
export class RolePickerComponent {
  readonly value = model.required<Role>();

  protected readonly roles = ASSIGNABLE_ROLES;
  protected readonly icons = ICONS;
  protected readonly label = roleLabel;
}
