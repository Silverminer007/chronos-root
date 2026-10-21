import { TestBed } from '@angular/core/testing';
import { ToastContainerComponent } from './toast-container.component';
import { ToastService } from './toast.service';

describe('ToastContainerComponent', () => {
  it('rendert Meldungen als Bootstrap-Toast und entfernt sie per Schließen-Button', async () => {
    const fixture = TestBed.createComponent(ToastContainerComponent);
    TestBed.inject(ToastService).add({ severity: 'error', summary: 'Fehler', detail: 'Details', life: 60000 });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const toast = el.querySelector('.toast');
    expect(toast?.classList).toContain('text-bg-danger');
    expect(toast?.textContent).toContain('Fehler');
    expect(toast?.textContent).toContain('Details');

    (el.querySelector('.btn-close') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('.toast')).toBeNull();
  });
});
