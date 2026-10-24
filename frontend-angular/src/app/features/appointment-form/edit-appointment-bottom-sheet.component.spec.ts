import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Appointment } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { EditAppointmentBottomSheetComponent } from './edit-appointment-bottom-sheet.component';

const base: Appointment = {
  id: 5,
  name: 'Zeltlager',
  description: 'Packliste',
  start: new Date('2026-10-21T18:00').toISOString(),
  end: new Date('2026-10-21T20:00').toISOString(),
  venue: 'Wiese',
  status: 'PLANNED',
  minimal_attendees: 3,
  participants: [],
  group_participants: [],
  messages: [],
};

describe('EditAppointmentBottomSheetComponent', () => {
  const store = { updateAppointment: vi.fn(), fetchAppointment: vi.fn() };
  const toast = { add: vi.fn(), success: vi.fn(), error: vi.fn() };
  let fixture: ComponentFixture<EditAppointmentBottomSheetComponent>;
  let saved: number;
  const el = () => fixture.nativeElement as HTMLElement;
  const button = (text: string) =>
    Array.from(el().querySelectorAll('button')).find((b) =>
      b.textContent?.includes(text),
    ) as HTMLButtonElement;
  const type = (selector: string, value: string) => {
    const input = el().querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const render = (appointment = base) => {
    fixture = TestBed.createComponent(EditAppointmentBottomSheetComponent);
    saved = 0;
    fixture.componentInstance.saved.subscribe(() => saved++);
    fixture.componentRef.setInput('appointment', appointment);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
  };

  beforeEach(() => {
    Object.values(store).forEach((m) => m.mockReset().mockResolvedValue(undefined));
    Object.values(toast).forEach((m) => m.mockReset());
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: ToastService, useValue: toast },
      ],
    });
  });

  it('rendert nichts, solange das Sheet geschlossen ist', () => {
    fixture = TestBed.createComponent(EditAppointmentBottomSheetComponent);
    fixture.componentRef.setInput('appointment', base);
    fixture.detectChanges();
    expect(el().querySelector('.sheet')).toBeNull();
  });

  it('füllt das Formular mit den Werten des Termins', () => {
    render();
    expect((el().querySelector('input[type="text"]') as HTMLInputElement).value).toBe('Zeltlager');
    expect((el().querySelector('textarea') as HTMLTextAreaElement).value).toBe('Packliste');
    expect((el().querySelector('input[type="datetime-local"]') as HTMLInputElement).value).toBe(
      '2026-10-21T18:00',
    );
    expect(button('2 Std').classList).toContain('btn-primary');
    expect(el().textContent).toContain('Mindestteilnehmer');
  });

  it('zeigt bei abweichender Dauer die eigene Dauer', () => {
    render({ ...base, end: new Date('2026-10-21T18:45').toISOString() });
    expect((el().querySelector('input[type="number"]') as HTMLInputElement).value).toBe('45');
  });

  it('deaktiviert Speichern ohne Änderungen und aktiviert es bei einer Änderung', () => {
    render();
    expect(button('Speichern').disabled).toBe(true);
    type('input[type="text"]', 'Neu');
    expect(button('Speichern').disabled).toBe(false);
  });

  it('deaktiviert Speichern bei leerem Namen', () => {
    render();
    type('input[type="text"]', '  ');
    expect(button('Speichern').disabled).toBe(true);
  });

  it('speichert Änderungen, lädt neu, meldet Erfolg und schließt', async () => {
    render();
    type('input[type="text"]', ' Sommerlager ');
    button('2 Std').click();
    button('6 Std').click();
    fixture.detectChanges();
    button('Speichern').click();
    await fixture.whenStable();
    expect(store.updateAppointment).toHaveBeenCalledWith(5, {
      name: 'Sommerlager',
      description: 'Packliste',
      venue: 'Wiese',
      start: base.start,
      end: new Date('2026-10-22T00:00').toISOString(),
      minimal_attendees: 3,
    });
    expect(store.fetchAppointment).toHaveBeenCalledWith(5);
    expect(toast.success).toHaveBeenCalledWith('Änderungen gespeichert');
    expect(saved).toBe(1);
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('setzt die Mindestteilnehmerzahl auf 0, wenn sie abgeschaltet wird', async () => {
    render();
    (fixture.nativeElement.querySelector('#edit-has-attendees') as HTMLInputElement).click();
    fixture.detectChanges();
    button('Speichern').click();
    await fixture.whenStable();
    expect(store.updateAppointment.mock.calls[0][1].minimal_attendees).toBe(0);
  });

  it('meldet einen Fehler und bleibt geöffnet', async () => {
    store.updateAppointment.mockRejectedValue(new Error('x'));
    render();
    type('input[type="text"]', 'Neu');
    button('Speichern').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(toast.error).toHaveBeenCalledWith(
      'Fehler',
      'Änderungen konnten nicht gespeichert werden',
    );
    expect(saved).toBe(0);
    expect(fixture.componentInstance.open()).toBe(true);
    expect(button('Speichern').disabled).toBe(false);
  });

  it('schließt über den Schließen-Button', () => {
    render();
    (el().querySelector('button[aria-label="Schließen"]') as HTMLElement).click();
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('schließt über den Hintergrund', () => {
    render();
    (el().querySelector('.sheet-backdrop') as HTMLElement).click();
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('übernimmt beim erneuten Öffnen wieder die Werte des Termins', () => {
    render();
    type('input[type="text"]', 'Verworfen');
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect((el().querySelector('input[type="text"]') as HTMLInputElement).value).toBe('Zeltlager');
  });
});
