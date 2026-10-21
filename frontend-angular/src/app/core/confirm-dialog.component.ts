import { ChangeDetectionStrategy, Component, HostListener, computed, inject } from '@angular/core';
import { ConfirmColor, ModalService } from './modal.service';

const COLOR_STYLE: Record<ConfirmColor, { variant: string; icon: string; confirmIcon: string }> = {
  red: { variant: 'danger', icon: 'bi-exclamation-triangle-fill', confirmIcon: 'bi-x-circle' },
  purple: { variant: 'primary', icon: 'bi-question-circle-fill', confirmIcon: 'bi-check-lg' },
  green: { variant: 'success', icon: 'bi-check-circle-fill', confirmIcon: 'bi-check-lg' },
};

/** Bootstrap-Modal für `ModalService.confirm`; einmal in `App` einbinden. */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (view(); as v) {
      <div class="modal-backdrop fade show"></div>
      <div
        class="modal fade show d-block"
        tabindex="-1"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
      >
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <i class="bi fs-3 me-3" [class]="v.style.icon + ' text-' + v.style.variant" aria-hidden="true"></i>
              <h2 class="modal-title fs-5" id="confirm-dialog-title">{{ v.request.title }}</h2>
            </div>
            <div class="modal-body">{{ v.request.message }}</div>
            <div class="modal-footer">
              <button type="button" class="btn btn-outline-secondary" (click)="modal.resolve(false)">Abbrechen</button>
              <button
                type="button"
                class="btn"
                [class]="v.request.confirmColor === 'purple' ? 'btn-gradient' : 'btn-' + v.style.variant"
                (click)="modal.resolve(true)"
              >
                <i class="bi me-2" [class]="v.style.confirmIcon" aria-hidden="true"></i>{{ v.request.confirmText }}
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmDialogComponent {
  protected readonly modal = inject(ModalService);

  protected readonly view = computed(() => {
    const request = this.modal.confirmRequest();
    return request ? { request, style: COLOR_STYLE[request.confirmColor] } : null;
  });

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.modal.confirmRequest()) this.modal.resolve(false);
  }
}
