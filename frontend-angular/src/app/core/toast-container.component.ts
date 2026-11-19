import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, ToastSeverity } from './toast.service';
import { IconComponent } from '../shared/icon/icon.component';

const SEVERITY_STYLE: Record<ToastSeverity, { background: string; icon: string }> = {
  success: { background: 'text-bg-success', icon: 'check-circle' },
  info: { background: 'text-bg-info', icon: 'info' },
  warn: { background: 'text-bg-warning', icon: 'triangle-alert' },
  error: { background: 'text-bg-danger', icon: 'ban' },
};

/** Rendert die aktiven Meldungen des `ToastService` als Bootstrap-Toasts. Einmal in `App` einbinden. */
@Component({
  imports: [IconComponent],
  selector: 'app-toast-container',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-container position-fixed top-0 end-0 p-3">
      @for (toast of toasts.toasts(); track toast.id) {
        <div
          class="toast show align-items-center border-0"
          [class]="style(toast.severity).background"
          [attr.role]="toast.severity === 'error' ? 'alert' : 'status'"
          aria-atomic="true"
        >
          <div class="d-flex">
            <div class="toast-body">
              <app-icon [name]="style(toast.severity).icon" class="me-2" />
              <strong>{{ toast.summary }}</strong>
              @if (toast.detail) {
                <div>{{ toast.detail }}</div>
              }
            </div>
            <button
              type="button"
              class="btn-close btn-close-white me-2 m-auto"
              aria-label="Schließen"
              (click)="toasts.remove(toast.id)"
            ></button>
          </div>
        </div>
      }
    </div>
  `,
})
export class ToastContainerComponent {
  protected readonly toasts = inject(ToastService);

  protected style(severity: ToastSeverity) {
    return SEVERITY_STYLE[severity];
  }
}
