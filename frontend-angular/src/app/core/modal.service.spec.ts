import { TestBed } from '@angular/core/testing';
import { ModalService } from './modal.service';

describe('ModalService', () => {
  let service: ModalService;

  beforeEach(() => {
    service = TestBed.inject(ModalService);
  });

  it('ist zunächst geschlossen', () => {
    expect(service.confirmRequest()).toBeNull();
  });

  it('öffnet den Bestätigungsdialog mit den Optionen und Standardfarbe Lila', () => {
    void service.confirm({ title: 'Löschen', message: 'Sicher?', confirmText: 'Löschen' });
    expect(service.confirmRequest()).toEqual(
      expect.objectContaining({ title: 'Löschen', message: 'Sicher?', confirmText: 'Löschen', confirmColor: 'purple' }),
    );
  });

  it('löst mit true auf, wenn bestätigt wird, und schließt den Dialog', async () => {
    const result = service.confirm({ title: 'T', message: 'M', confirmText: 'Ja' });
    service.resolve(true);
    expect(await result).toBe(true);
    expect(service.confirmRequest()).toBeNull();
  });

  it('löst mit false auf, wenn abgebrochen wird', async () => {
    const result = service.confirm({ title: 'T', message: 'M', confirmText: 'Ja', confirmColor: 'red' });
    service.resolve(false);
    expect(await result).toBe(false);
  });

  it('bricht einen noch offenen Dialog ab, wenn ein neuer geöffnet wird', async () => {
    const first = service.confirm({ title: 'A', message: 'M', confirmText: 'Ja' });
    void service.confirm({ title: 'B', message: 'M', confirmText: 'Ja' });
    expect(await first).toBe(false);
    expect(service.confirmRequest()?.title).toBe('B');
  });
});
