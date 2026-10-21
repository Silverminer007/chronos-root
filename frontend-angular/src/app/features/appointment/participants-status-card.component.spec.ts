import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { User } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { AppointmentStore } from '../agenda/appointment.store';
import { ParticipantLookupService } from './participant-lookup.service';
import { ParticipantsStatusCardComponent } from './participants-status-card.component';
import { appointment, buttonByText, click, participant } from './testing';

describe('ParticipantsStatusCardComponent', () => {
  let store: Record<'addParticipant' | 'removeParticipant' | 'changeParticipantRole', ReturnType<typeof vi.fn>>;
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };
  const user = signal<User | null>(null);

  const participants = [
    participant(1, { name: 'Org', role: 'RESPONSIBLE', status: 'APPROVED' }),
    participant(2, { name: 'Zoe', role: 'ATTENDANT', status: 'PENDING' }),
    participant(3, { name: 'Bea', role: 'ATTENDANT', status: 'APPROVED', via_group_name: 'Jugend' }),
    participant(4, { name: 'Gast', role: 'GUEST', status: 'REJECTED' }),
  ];

  const render = (userId: number | null) => {
    user.set(userId === null ? null : { id: userId, first_name: 'M', last_name: 'M', email: null });
    store = {
      addParticipant: vi.fn().mockResolvedValue(undefined),
      removeParticipant: vi.fn().mockResolvedValue(undefined),
      changeParticipantRole: vi.fn().mockResolvedValue(undefined),
    };
    toast = { success: vi.fn(), error: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AppointmentStore, useValue: store },
        { provide: AuthService, useValue: { user } },
        { provide: ToastService, useValue: toast },
        { provide: ParticipantLookupService, useValue: { loadFriends: vi.fn().mockResolvedValue([]) } },
      ],
    });
    const fixture = TestBed.createComponent(ParticipantsStatusCardComponent);
    fixture.componentRef.setInput('appointment', appointment({ participants }));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, rows: () => Array.from(el.querySelectorAll('[data-testid="participant"]')) };
  };

  it('zeigt Zusagen-Zähler und Fortschritt', () => {
    const { el } = render(2);
    expect(el.textContent).toContain('2 / 4 zugesagt');
    expect((el.querySelector('.progress-bar') as HTMLElement).style.width).toBe('50%');
  });

  it('sortiert nach Rolle, dann Status, dann Name', () => {
    const { rows } = render(2);
    expect(rows().map((r) => r.querySelector('[data-testid="name"]')?.textContent?.trim())).toEqual(['Org', 'Bea', 'Zoe', 'Gast']);
  });

  it('zeigt Rolle, Gruppe und Status je Person', () => {
    const { rows } = render(2);
    const bea = rows()[1].textContent ?? '';
    expect(bea).toContain('Teilnehmer');
    expect(bea).toContain('Jugend');
    expect(bea).toContain('Zugesagt');
    expect(rows()[3].textContent).toContain('Abgesagt');
    expect(rows()[2].textContent).toContain('Ausstehend');
  });

  it('zeigt Hinzufügen- und Menü-Buttons nur Organisatoren (Menü nicht für Organisatoren selbst)', () => {
    const plain = render(2);
    expect(plain.el.querySelector('button[aria-label="Teilnehmer hinzufügen"]')).toBeNull();
    expect(plain.el.querySelector('button[aria-label^="Aktionen für"]')).toBeNull();
    TestBed.resetTestingModule();

    const org = render(1);
    expect(org.el.querySelector('button[aria-label="Teilnehmer hinzufügen"]')).not.toBeNull();
    expect(org.el.querySelectorAll('button[aria-label^="Aktionen für"]')).toHaveLength(3);
    expect(org.el.querySelector('button[aria-label="Aktionen für Org"]')).toBeNull();
  });

  it('ändert die Rolle über das Menü (aktuelle Rolle wird nicht angeboten)', async () => {
    const { fixture, el } = render(1);
    click(el.querySelector('button[aria-label="Aktionen für Zoe"]'));
    fixture.detectChanges();
    const items = Array.from(el.querySelectorAll('[role="menuitem"]')).map((i) => i.textContent?.trim());
    expect(items).toEqual(['Organisator', 'Helfer', 'Gast', 'Entfernen']);

    click(buttonByText(el, 'Helfer'));
    await fixture.whenStable();
    expect(store.changeParticipantRole).toHaveBeenCalledWith(5, 2, 'HELPER');
    expect(toast.success).toHaveBeenCalledWith('Rolle geändert', 'Die Teilnehmerrolle wurde erfolgreich geändert');
    fixture.detectChanges();
    expect(el.querySelector('[role="menu"]')).toBeNull();
  });

  it('meldet Fehler beim Ändern der Rolle', async () => {
    const { fixture, el } = render(1);
    store.changeParticipantRole.mockRejectedValue(new Error('x'));
    click(el.querySelector('button[aria-label="Aktionen für Zoe"]'));
    fixture.detectChanges();
    click(buttonByText(el, 'Helfer'));
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Rolle konnte nicht geändert werden');
  });

  it('entfernt eine Person über das Menü', async () => {
    const { fixture, el } = render(1);
    click(el.querySelector('button[aria-label="Aktionen für Zoe"]'));
    fixture.detectChanges();
    click(buttonByText(el, 'Entfernen'));
    await fixture.whenStable();
    expect(store.removeParticipant).toHaveBeenCalledWith(5, 2);
    expect(toast.success).toHaveBeenCalledWith('Teilnehmer entfernt', 'Zoe wurde entfernt');
  });

  it('meldet Fehler beim Entfernen', async () => {
    const { fixture, el } = render(1);
    store.removeParticipant.mockRejectedValue(new Error('x'));
    click(el.querySelector('button[aria-label="Aktionen für Zoe"]'));
    fixture.detectChanges();
    click(buttonByText(el, 'Entfernen'));
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Teilnehmer konnte nicht entfernt werden');
  });

  it('schließt das Menü bei Klick außerhalb', () => {
    const { fixture, el } = render(1);
    click(el.querySelector('button[aria-label="Aktionen für Zoe"]'));
    fixture.detectChanges();
    expect(el.querySelector('[role="menu"]')).not.toBeNull();
    document.body.click();
    fixture.detectChanges();
    expect(el.querySelector('[role="menu"]')).toBeNull();
  });

  it('fügt über den Dialog eine Person hinzu', async () => {
    const { fixture, el } = render(1);
    click(el.querySelector('button[aria-label="Teilnehmer hinzufügen"]'));
    fixture.detectChanges();
    expect(el.querySelector('app-add-participant-dialog .modal')).not.toBeNull();

    const dialog = fixture.debugElement.query((d) => d.name === 'app-add-participant-dialog');
    dialog.componentInstance.added.emit({ id: 9, role: 'GUEST' });
    await fixture.whenStable();
    expect(store.addParticipant).toHaveBeenCalledWith(5, 9, 'GUEST');
    expect(toast.success).toHaveBeenCalledWith('Teilnehmer hinzugefügt', 'Der Teilnehmer wurde erfolgreich hinzugefügt');
  });

  it('meldet Fehler beim Hinzufügen', async () => {
    const { fixture } = render(1);
    store.addParticipant.mockRejectedValue(new Error('x'));
    const dialog = fixture.debugElement.query((d) => d.name === 'app-add-participant-dialog');
    dialog.componentInstance.added.emit({ id: 9, role: 'GUEST' });
    await fixture.whenStable();
    expect(toast.error).toHaveBeenCalledWith('Fehler', 'Teilnehmer konnte nicht hinzugefügt werden');
  });
});
