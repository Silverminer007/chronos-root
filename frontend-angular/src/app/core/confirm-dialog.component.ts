import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
} from '@angular/core';
import { ConfirmColor, ModalService } from './modal.service';
import { IconComponent } from '../shared/icon/icon.component';

const COLOR_STYLE: Record<ConfirmColor, { variant: string; icon: string; confirmIcon: string }> = {
  red: { variant: 'danger', icon: 'triangle-alert', confirmIcon: 'x-circle' },
  purple: { variant: 'primary', icon: 'circle-help', confirmIcon: 'check' },
  green: { variant: 'success', icon: 'check-circle', confirmIcon: 'check' },
};

/** Bootstrap-Modal für `ModalService.confirm`; einmal in `App` einbinden. */
@Component({
  imports: [IconComponent],
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
              <app-icon [name]="v.style.icon" [class]="'fs-3 me-3 text-' + v.style.variant" />
              <h2 class="modal-title fs-5" id="confirm-dialog-title">{{ v.request.title }}</h2>
            </div>
            <div class="modal-body">{{ v.request.message }}</div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="modal.resolve(false)">Abbrechen</button>
              <button
                type="button"
                class="btn"
                data-autofocus
                [class]="v.request.confirmColor === 'purple' ? 'btn-gradient' : 'btn-' + v.style.variant"
                (click)="modal.resolve(true)"
              >
                <app-icon [name]="v.style.confirmIcon" class="me-2" />{{ v.request.confirmText }}
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
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private opener: HTMLElement | null = null;

  protected readonly view = computed(() => {
    const request = this.modal.confirmRequest();
    return request ? { request, style: COLOR_STYLE[request.confirmColor] } : null;
  });

  constructor() {
    effect(() => {
      if (this.view()) {
        this.opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('[data-autofocus]')?.focus(), {
          injector: this.injector,
        });
      } else if (this.opener) {
        const opener = this.opener;
        this.opener = null;
        opener.focus();
      }
    });
  }

  /** Hält den Tab-Fokus im Dialog, solange er offen ist. */
  @HostListener('document:keydown.tab', ['$event'])
  @HostListener('document:keydown.shift.tab', ['$event'])
  protected onTab(event: Event): void {
    if (!this.modal.confirmRequest()) return;
    const buttons = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('.modal-footer button'));
    if (buttons.length === 0) return;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    const shift = (event as KeyboardEvent).shiftKey;
    const active = document.activeElement;
    if (!buttons.includes(active as HTMLElement) || (shift && active === first) || (!shift && active === last)) {
      event.preventDefault();
      (shift ? last : first).focus();
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.modal.confirmRequest()) this.modal.resolve(false);
  }
}
