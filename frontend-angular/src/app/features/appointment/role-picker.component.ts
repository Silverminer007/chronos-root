import { ChangeDetectionStrategy, Component, model } from '@angular/core';
import { Role } from '../../core/models';
import { ASSIGNABLE_ROLES, roleLabel } from './participation';

const ICONS: Record<string, string> = {
  RESPONSIBLE: 'bi-star',
  ATTENDANT: 'bi-person',
  HELPER: 'bi-wrench',
  GUEST: 'bi-eye',
};

/** Auswahl der Rolle in den Hinzufügen-Dialogen (je Rolle eine Kachel). */
@Component({
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
            <i class="bi {{ icons[role] }} d-block mb-1" aria-hidden="true"></i>
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
