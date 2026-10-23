import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('zeigt eine Meldung mit Severity, Titel und Text', () => {
    service.add({ severity: 'success', summary: 'Gespeichert', detail: 'Termin angelegt' });
    expect(service.toasts()).toEqual([
      expect.objectContaining({ severity: 'success', summary: 'Gespeichert', detail: 'Termin angelegt' }),
    ]);
  });

  it('blendet die Meldung nach der Standarddauer automatisch aus', () => {
    service.add({ severity: 'info', summary: 'Hinweis' });
    vi.advanceTimersByTime(ToastService.DEFAULT_LIFE_MS - 1);
    expect(service.toasts()).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(service.toasts()).toHaveLength(0);
  });

  it('beachtet eine eigene Dauer', () => {
    service.add({ severity: 'warn', summary: 'Achtung', life: 100 });
    vi.advanceTimersByTime(100);
    expect(service.toasts()).toHaveLength(0);
  });

  it('entfernt eine Meldung manuell', () => {
    service.add({ severity: 'error', summary: 'Fehler' });
    service.add({ severity: 'error', summary: 'Noch ein Fehler' });
    const [first] = service.toasts();
    service.remove(first.id);
    expect(service.toasts().map((t) => t.summary)).toEqual(['Noch ein Fehler']);
  });

  it('bietet Kurzformen je Severity', () => {
    service.error('E', 'd');
    service.success('S');
    expect(service.toasts().map((t) => t.severity)).toEqual(['error', 'success']);
  });
});
