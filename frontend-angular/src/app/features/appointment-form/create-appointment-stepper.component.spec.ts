import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { GroupStore } from '../groups/group.store';
import { FriendshipStore } from '../friends/friendship.store';
import { CreateAppointmentStepperComponent } from './create-appointment-stepper.component';

describe('CreateAppointmentStepperComponent', () => {
  const store = {
    createAppointment: vi.fn(),
    addParticipant: vi.fn(),
    addGroupParticipant: vi.fn(),
  };
  const toast = { add: vi.fn(), error: vi.fn() };
  let fixture: ComponentFixture<CreateAppointmentStepperComponent>;
  let created: number[];
  const el = () => fixture.nativeElement as HTMLElement;

  const next = () => {
    button('Weiter').click();
    fixture.detectChanges();
  };
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
  const heading = () => el().querySelector('h1')?.textContent?.trim();

  beforeEach(() => {
    Object.values(store).forEach((m) => m.mockReset());
    Object.values(toast).forEach((m) => m.mockReset());
    store.createAppointment.mockResolvedValue({ id: 42 });
    store.addParticipant.mockResolvedValue(undefined);
    store.addGroupParticipant.mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: ToastService, useValue: toast },
        {
          provide: GroupStore,
          useValue: { searchGroups: vi.fn() },
        },
        { provide: FriendshipStore, useValue: { fetchFriends: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(CreateAppointmentStepperComponent);
    created = [];
    fixture.componentInstance.created.subscribe((id) => created.push(id));
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
  });

  it('beginnt mit dem Namensschritt, Weiter ist ohne Namen deaktiviert', () => {
    expect(heading()).toBe('Wie heißt der Termin?');
    expect(button('Weiter').disabled).toBe(true);
    expect(button('Abbrechen')).toBeTruthy();
  });

  it('schaltet Weiter frei, sobald ein Name eingegeben ist, und geht Schritt für Schritt vor und zurück', () => {
    type('input[type="text"]', 'Sommerfest');
    expect(button('Weiter').disabled).toBe(false);
    next();
    expect(heading()).toBe('Wann?');
    next();
    expect(heading()).toBe('Wie lange?');
    next();
    expect(heading()).toBe('Beschreibung');
    next();
    expect(heading()).toBe('Wo findet es statt?');
    next();
    expect(heading()).toBe('Teilnehmer');
    expect(button('Erstellen')).toBeTruthy();
    button('Zurück').click();
    fixture.detectChanges();
    expect(heading()).toBe('Wo findet es statt?');
  });

  it('blockiert Schritt 3 ohne gültige Dauer', () => {
    type('input[type="text"]', 'A');
    next();
    next();
    button('Eigene Dauer').click();
    fixture.detectChanges();
    type('input[type="number"]', '0');
    expect(button('Weiter').disabled).toBe(true);
    type('input[type="number"]', '2');
    expect(button('Weiter').disabled).toBe(false);
  });

  it('setzt über die Schnellauswahl das Datum und behält die Uhrzeit', () => {
    type('input[type="text"]', 'A');
    next();
    const input = el().querySelector('input[type="datetime-local"]') as HTMLInputElement;
    input.value = '2020-01-01T14:30';
    input.dispatchEvent(new Event('input'));
    button('Morgen').click();
    fixture.detectChanges();
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const pad = (n: number) => String(n).padStart(2, '0');
    expect(input.value).toBe(
      `${tomorrow.getFullYear()}-${pad(tomorrow.getMonth() + 1)}-${pad(tomorrow.getDate())}T14:30`,
    );
  });

  it('zeigt die Zusammenfassung im linken Panel', () => {
    type('input[type="text"]', 'Sommerfest');
    expect(el().querySelector('.summary-title')?.textContent).toContain('Sommerfest');
  });

  it('erstellt den Termin mit allen Eingaben, lädt Eingeladene ein, schließt und meldet die ID', async () => {
    type('input[type="text"]', ' Sommerfest ');
    next();
    next();
    button('2 Std').click();
    next();
    type('textarea', 'Bitte Kuchen mitbringen');
    next();
    type('input[type="text"]', 'Vereinsheim');
    next();
    button('Ja').click();
    fixture.detectChanges();
    button('+').click();
    fixture.detectChanges();
    const picker = fixture.componentInstance['picker']()!;
    picker.selectedUsers.set([{ id: 1, name: 'Anna', role: 'HELPER' }]);
    picker.selectedGroups.set([{ id: 2, name: 'Jugend', role: 'ATTENDANT', memberCount: 3 }]);

    button('Erstellen').click();
    await fixture.whenStable();

    const payload = store.createAppointment.mock.calls[0][0];
    expect(payload).toMatchObject({
      name: 'Sommerfest',
      description: 'Bitte Kuchen mitbringen',
      venue: 'Vereinsheim',
      minimal_attendees: 6,
    });
    expect(new Date(payload.end).getTime() - new Date(payload.start).getTime()).toBe(120 * 60_000);
    expect(store.addParticipant).toHaveBeenCalledWith(42, 1, 'HELPER');
    expect(store.addGroupParticipant).toHaveBeenCalledWith(42, 2, 'ATTENDANT');
    expect(toast.add).not.toHaveBeenCalled();
    expect(created).toEqual([42]);
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('warnt, wenn einzelne Einladungen scheitern, und erstellt den Termin trotzdem', async () => {
    store.addParticipant.mockRejectedValue(new Error('x'));
    type('input[type="text"]', 'A');
    for (let i = 0; i < 5; i++) next();
    fixture.componentInstance['picker']()!.selectedUsers.set([
      { id: 1, name: 'A', role: 'ATTENDANT' },
      { id: 2, name: 'B', role: 'ATTENDANT' },
    ]);
    button('Erstellen').click();
    await fixture.whenStable();
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'warn',
        summary: 'Teilweise erfolgreich',
        detail: 'Termin erstellt, aber 2 Einladungen konnten nicht gesendet werden.',
      }),
    );
    expect(created).toEqual([42]);
  });

  it('zeigt bei einer gescheiterten Einladung die Einzahl', async () => {
    store.addParticipant.mockRejectedValue(new Error('x'));
    type('input[type="text"]', 'A');
    for (let i = 0; i < 5; i++) next();
    fixture.componentInstance['picker']()!.selectedUsers.set([
      { id: 1, name: 'A', role: 'ATTENDANT' },
    ]);
    button('Erstellen').click();
    await fixture.whenStable();
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: 'Termin erstellt, aber 1 Einladung konnte nicht gesendet werden.',
      }),
    );
  });

  it('meldet einen Fehler beim Erstellen und bleibt geöffnet', async () => {
    store.createAppointment.mockRejectedValue(new Error('x'));
    type('input[type="text"]', 'A');
    for (let i = 0; i < 5; i++) next();
    button('Erstellen').click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Termin konnte nicht erstellt werden');
    expect(created).toEqual([]);
    expect(fixture.componentInstance.open()).toBe(true);
    expect(button('Erstellen').disabled).toBe(false);
  });

  it('schließt über Abbrechen im ersten Schritt', () => {
    button('Abbrechen').click();
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('wechselt per Wischgeste: links = weiter (wenn möglich), rechts = zurück', () => {
    type('input[type="text"]', 'A');
    const root = el().querySelector('.stepper') as HTMLElement;
    const swipe = (from: number, to: number) => {
      root.dispatchEvent(new TouchEvent('touchstart', { touches: [{ clientX: from } as Touch] }));
      root.dispatchEvent(
        new TouchEvent('touchend', { changedTouches: [{ clientX: to } as Touch] }),
      );
      fixture.detectChanges();
    };
    swipe(200, 100);
    expect(heading()).toBe('Wann?');
    swipe(100, 130); // zu kurz
    expect(heading()).toBe('Wann?');
    swipe(100, 200);
    expect(heading()).toBe('Wie heißt der Termin?');
  });

  it('setzt das Formular beim erneuten Öffnen zurück', () => {
    type('input[type="text"]', 'Alt');
    next();
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();
    expect(el().querySelector('.stepper')).toBeNull();
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();
    expect(heading()).toBe('Wie heißt der Termin?');
    expect((el().querySelector('input[type="text"]') as HTMLInputElement).value).toBe('');
  });
});
